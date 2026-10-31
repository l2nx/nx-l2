/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.integration.prometheus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.app.prometheus.PrometheusClient;
import app.l2nx.load.app.prometheus.PrometheusServerMeters;
import app.l2nx.load.app.prometheus.ServerMeterQuery;
import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.web.client.RestClient;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

// Sequential: a test that fails scrapes would zero the window of a test running beside it.
@Execution(ExecutionMode.SAME_THREAD)
class PrometheusServerMetersTest {
    /** Kept equal to the image of the compose profile {@code load}. */
    static final String IMAGE = "prom/prometheus:v3.13.5";

    private static final Duration FIRST_SCRAPE = Duration.ofSeconds(15);
    private static final Duration WINDOW = Duration.ofSeconds(5);
    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(1);

    private static MetricsStub stub;
    private static GenericContainer<?> prometheus;
    private static PrometheusClient client;

    @BeforeAll
    static void start() {
        stub = MetricsStub.start();
        // Forwarded through the Testcontainers SSH sidecar: reaches a stub on the host's loopback with any Docker.
        Testcontainers.exposeHostPorts(stub.port());
        prometheus = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withCopyToContainer(Transferable.of(config(stub.port())), "/etc/prometheus/prometheus.yml")
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/-/ready").forStatusCode(200));
        prometheus.start();
        client = new PrometheusClient(
                RestClient.builder(),
                URI.create("http://" + prometheus.getHost() + ":" + prometheus.getMappedPort(9090)));
    }

    @AfterAll
    static void stop() {
        if (prometheus != null) {
            prometheus.stop();
        }
        if (stub != null) {
            stub.close();
        }
    }

    @Nested
    class CheckReady {
        @Test
        void checkReady_shouldPass_whenTheTargetIsUp() {
            awaitFirstScrape();

            assertThatNoException().isThrownBy(() -> meters(target("game")).checkReady());
        }

        @Test
        void checkReady_shouldFail_whenNoJobIsScraped() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> meters(target("login")).checkReady())
                    .withMessageContaining("no job login");
        }

        @Test
        void checkReady_shouldFail_whenPrometheusIsDown() throws IOException {
            final PrometheusClient down = new PrometheusClient(RestClient.builder(), closedPort());

            assertThatIllegalStateException()
                    .isThrownBy(() -> new PrometheusServerMeters(down, settings(down, target("game"))).checkReady())
                    .withMessageContaining("not ready");
        }
    }

    @Nested
    class ScrapeIntervals {
        @Test
        void scrapeIntervals_shouldReadTheIntervalOfTheConfig() {
            awaitFirstScrape();

            assertThat(client.scrapeIntervals()).isEqualTo(Map.of("game", Duration.ofSeconds(1)));
        }
    }

    @Nested
    class Finish {
        @Test
        void finish_shouldReadTheWindowWithThePromQlOfTheReport() throws InterruptedException {
            awaitFirstScrape();
            final PrometheusServerMeters meters = meters(target("game"));
            meters.checkReady();
            final Instant start = Instant.now();
            meters.start();
            Thread.sleep(WINDOW.dividedBy(2));
            stub.registerJdbcOnLoop();
            Thread.sleep(WINDOW.dividedBy(2));
            final ServerWindow window = ServerWindow.of(start, Instant.now());

            final ServerStats actual = meters.finish(window);

            assertThat(actual).isNotNull();
            final LoopStats world = actual.loops().get("world");
            assertThat(world).isNotNull();
            assertThat(world.passP99Millis()).isCloseTo(10.0, within(1e-6));
            assertThat(world.passP50Millis()).isCloseTo(0.005 * 50 / 90 * Sample.MILLIS_PER_SECOND, within(1e-6));
            assertThat(world.passMaxMillis())
                    .as("the 30 ms gauge capped at the highest bucket the window filled")
                    .isCloseTo(25.0, within(1e-6));
            assertThat(world.jdbcOnLoop())
                    .as("a series registered inside the window counts from zero")
                    .isEqualTo(MetricsStub.JDBC_CALLS);
            assertThat(world.inboxDepthMax()).isEqualTo(3.0);
            assertThat(meters.snapshots())
                    .containsOnlyKeys("server-metrics-game-start.prom", "server-metrics-game-end.prom")
                    .allSatisfy((file, text) -> assertThat(text).contains("l2nx_engine_pass_seconds_bucket"));
            final List<Sample> dashboard =
                    client.query(ServerMeterQuery.PASS_P99.expression(window.rangeMillis()), window.end());
            assertThat(dashboard)
                    .singleElement()
                    .satisfies(sample -> assertThat(sample.value() * Sample.MILLIS_PER_SECOND)
                            .isEqualTo(world.passP99Millis()));
        }
    }

    @Nested
    class FinishAfterAFailedScrape {
        @Test
        void finish_shouldReadNoMeters_whenAScrapeFailedInTheWindow() throws InterruptedException {
            awaitFirstScrape();
            final PrometheusServerMeters meters = meters(target("game"));
            meters.checkReady();
            final Instant start = Instant.now();
            meters.start();
            stub.failScrapes(true);
            try {
                Thread.sleep(WINDOW.dividedBy(2));
            } finally {
                stub.failScrapes(false);
            }
            Thread.sleep(WINDOW.dividedBy(2));
            final ServerWindow window = ServerWindow.of(start, Instant.now());

            final ServerStats actual = meters.finish(window);

            assertThat(actual).isNull();
        }
    }

    private static void awaitFirstScrape() {
        await().atMost(FIRST_SCRAPE)
                .until(() -> client.query("up{job=\"game\"}", null).stream().anyMatch(sample -> sample.value() == 1));
    }

    private static PrometheusServerMeters meters(LoadProperties.Target target) {
        return new PrometheusServerMeters(client, settings(client, target));
    }

    private static LoadProperties.Prometheus settings(PrometheusClient prometheus, LoadProperties.Target target) {
        return LoadProperties.Prometheus.builder()
                .url(prometheus.url())
                .targets(List.of(target))
                .maxClockSkew(MAX_CLOCK_SKEW)
                .build();
    }

    private static LoadProperties.Target target(String job) {
        return new LoadProperties.Target(job, stub.metricsUrl());
    }

    private static URI closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }
    }

    private static String config(int port) {
        return """
            global:
              scrape_interval: 1s
              scrape_timeout: 1s
            scrape_configs:
              - job_name: game
                metrics_path: %s
                static_configs:
                  - targets: ["host.testcontainers.internal:%d"]
            """.formatted(MetricsStub.PATH, port);
    }
}
