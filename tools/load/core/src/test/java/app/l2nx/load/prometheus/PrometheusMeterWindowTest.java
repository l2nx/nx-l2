/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import static app.l2nx.load.prometheus.MetricFamilies.family;
import static app.l2nx.load.prometheus.MetricFamilies.gauge;
import static app.l2nx.load.prometheus.MetricFamilies.histogramMetric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.load.report.model.ServerStats;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.Metrics.MetricType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PrometheusMeterWindowTest {
    private static final int ATTEMPTS = PrometheusMeterWindow.BOUNDARY_RETRIES + 1;

    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private PrometheusScraper scraper;

    @AfterEach
    void stop() {
        scraper.close();
        server.stop(0);
    }

    @Nested
    class Finish {
        @Test
        void finish_shouldReadEveryScrapeAndKeepTheBoundaryTexts_whenAllScrapesSucceed() {
            final PrometheusMeterWindow window = window(request -> false);

            window.start();
            window.sample();
            window.sample();
            final ServerStats actual = window.finish();

            assertThat(actual.processCpuMean()).isCloseTo(0.25, within(1e-9));
            assertThat(window.getStartText()).isEqualTo(text(1));
            assertThat(window.getEndText()).isEqualTo(text(4));
        }

        @Test
        void finish_shouldRetryTheEndScrape_whenItFailsOnce() {
            final PrometheusMeterWindow window = window(request -> request == 3);
            window.start();
            window.sample();

            final ServerStats actual = window.finish();

            assertThat(actual.processCpuMean()).isCloseTo(0.7 / 3, within(1e-9));
            assertThat(window.getEndText()).isEqualTo(text(4));
        }

        @Test
        void finish_shouldReturnNoServerStats_whenEveryEndAttemptFails() {
            final PrometheusMeterWindow window = window(request -> request >= 3);
            window.start();
            window.sample();

            final ServerStats actual = window.finish();

            assertThat(actual).isNull();
            assertThat(requests).hasValue(2 + ATTEMPTS);
            assertThat(window.getStartText()).isEqualTo(text(1));
            assertThat(window.getEndText()).isNull();
        }

        @Test
        void finish_shouldReturnNoServerStats_whenTheWindowMetersAreUnreadable() {
            final PrometheusMeterWindow window =
                    window(request -> false, PrometheusMeterWindowTest::passWithoutCounters);
            window.start();
            window.sample();

            final ServerStats actual = window.finish();

            assertThat(actual).isNull();
            assertThat(window.getEndText()).isEqualTo(text(3));
        }

        @Test
        void finish_shouldFail_whenTheWindowHasNotStarted() {
            final PrometheusMeterWindow window = window(request -> false);

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(window::finish);
        }
    }

    @Nested
    class Sample {
        @Test
        void sample_shouldSkipTheScrape_whenItFails() {
            final PrometheusMeterWindow window = window(request -> request == 2);

            window.start();
            window.sample();
            window.sample();
            final ServerStats actual = window.finish();

            assertThat(actual.processCpuMean()).isCloseTo(0.8 / 3, within(1e-9));
            assertThat(window.getEndText()).isEqualTo(text(4));
        }

        @Test
        void sample_shouldFail_whenTheWindowHasNotStarted() {
            final PrometheusMeterWindow window = window(request -> false);

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(window::sample);
        }
    }

    @Nested
    class Start {
        @Test
        void start_shouldRetryTheScrape_whenItFailsOnce() {
            final PrometheusMeterWindow window = window(request -> request == 1);

            window.start();

            assertThat(window.getStartText()).isEqualTo(text(2));
        }

        @Test
        void start_shouldLeaveTheServerMetersOut_whenEveryAttemptFails() {
            final PrometheusMeterWindow window = window(request -> request <= ATTEMPTS);

            window.start();
            window.sample();
            final ServerStats actual = window.finish();

            assertThat(actual).isNull();
            assertThat(requests)
                    .as("no sample or end scrape after a failed start")
                    .hasValue(ATTEMPTS);
            assertThat(window.getStartText()).isNull();
            assertThat(window.getEndText()).isNull();
        }
    }

    /** Numbers protobuf requests from 1; each text exposition names the request it follows, telling start from end. */
    private PrometheusMeterWindow window(IntPredicate failing) {
        return window(failing, PrometheusMeterWindowTest::cpu);
    }

    private PrometheusMeterWindow window(IntPredicate failing, IntFunction<byte[]> body) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/actuator/prometheus", exchange -> {
            try (exchange) {
                final String accept = exchange.getRequestHeaders().getFirst("Accept");
                if (accept.startsWith("application/vnd.google.protobuf")) {
                    final int request = requests.incrementAndGet();
                    final int status = failing.test(request) ? 503 : 200;
                    respond(exchange, status, PrometheusScraper.PROTOBUF, body.apply(request));
                } else {
                    respond(
                            exchange,
                            200,
                            "text/plain; version=0.0.4; charset=utf-8",
                            text(requests.get()).getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.start();
        final URI endpoint =
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/actuator/prometheus");
        scraper = new PrometheusScraper(endpoint, Duration.ofSeconds(5));
        return new PrometheusMeterWindow(scraper, Duration.ZERO);
    }

    private static String text(int request) {
        return String.format(Locale.ROOT, """
            # TYPE process_cpu_usage gauge
            process_cpu_usage %.1f
            """, request / 10.0);
    }

    private static byte[] cpu(int request) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            gauge(ServerStatsReader.PROCESS_CPU, request / 10.0).writeDelimitedTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] passWithoutCounters(int request) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            family(
                            ServerStatsReader.PASS,
                            MetricType.HISTOGRAM,
                            histogramMetric(
                                    new double[] {0.001, 0.01},
                                    new long[] {request, request},
                                    request,
                                    request * 0.001,
                                    "loop",
                                    "world"))
                    .writeDelimitedTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
