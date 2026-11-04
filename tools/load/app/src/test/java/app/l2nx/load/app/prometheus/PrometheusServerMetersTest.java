/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class PrometheusServerMetersTest {
    private static final URI PROMETHEUS = URI.create("http://127.0.0.1:9090");
    private static final URI GAME_METRICS = URI.create("http://127.0.0.1:8777/actuator/prometheus");
    private static final URI LOGIN_METRICS = URI.create("http://127.0.0.1:8106/actuator/prometheus");
    private static final List<LoadProperties.Target> TARGETS =
            List.of(new LoadProperties.Target("game", GAME_METRICS), new LoadProperties.Target("login", LOGIN_METRICS));
    private static final Map<String, Duration> SCRAPE_INTERVALS =
            Map.of("game", Duration.ofMillis(1_500), "login", Duration.ofSeconds(1));
    private static final Instant END = Instant.parse("2026-10-31T10:02:00.250Z");
    private static final ServerWindow WINDOW = ServerWindow.of(END.minusSeconds(120), END);
    private static final long RANGE_MILLIS = 120_000;
    private static final String LAST_SCRAPE = ServerMeterQuery.lastScrape(List.of("game", "login"));
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final Instant GAME_STARTED_AT = Instant.parse("2026-10-31T09:58:00Z");
    private static final Instant LOGIN_STARTED_AT = Instant.parse("2026-10-31T09:57:30Z");
    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(1);
    private static final Duration INGEST_WAIT = Duration.ofMillis(100);
    private static final Duration POLL_TIME = Duration.ofMillis(40);

    private final PrometheusClient client = mock(PrometheusClient.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    // No poll sleep: the injected clock alone runs the ingest wait out, advanced by each stubbed poll.
    private final PrometheusServerMeters meters =
            new PrometheusServerMeters(client, TARGETS, MAX_CLOCK_SKEW, now::get, INGEST_WAIT, Duration.ZERO);

    @Nested
    class CheckReady {
        @Test
        void checkReady_shouldFail_whenPrometheusIsNotReady() {
            when(client.ready()).thenReturn(false);
            when(client.url()).thenReturn(PROMETHEUS);

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining("Prometheus at http://127.0.0.1:9090 is not ready");
        }

        @ParameterizedTest(name = "Prometheus {0} ms off")
        @ValueSource(longs = {-1_001, 1_001})
        void checkReady_shouldFailNamingBothTimes_whenTheClocksDifferByMoreThanTheLimit(long skewMillis) {
            final Instant prometheus = NOW.plusMillis(skewMillis);
            when(client.ready()).thenReturn(true);
            when(client.query(ServerMeterQuery.now(), null)).thenReturn(List.of(scrapedAt(prometheus)));

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining(prometheus.toString())
                    .withMessageContaining(NOW.toString())
                    .withMessageContaining("1001 ms");
            verify(client, never()).query(ServerMeterQuery.up("game"), null);
        }

        @ParameterizedTest(name = "Prometheus {0} ms off")
        @ValueSource(longs = {-1_000, 0, 1_000})
        void checkReady_shouldPass_whenTheClocksDifferUpToTheLimit(long skewMillis) {
            stubReady();
            when(client.query(ServerMeterQuery.now(), null)).thenReturn(List.of(scrapedAt(NOW.plusMillis(skewMillis))));

            meters.checkReady();

            verify(client).query(ServerMeterQuery.up("login"), null);
        }

        @Test
        void checkReady_shouldFail_whenPrometheusScrapesNoSuchJob() {
            stubReady();
            when(client.scrapeIntervals()).thenReturn(Map.of("game", Duration.ofSeconds(1)));

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining("Prometheus scrapes no job login");
        }

        @Test
        void checkReady_shouldFail_whenAJobHasNoScrapeYet() {
            stubReady();
            when(client.query(ServerMeterQuery.up("login"), null)).thenReturn(List.of());

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining("Prometheus has not scraped job login yet");
        }

        @Test
        void checkReady_shouldFail_whenATargetIsDown() {
            stubReady();
            when(client.query(ServerMeterQuery.up("game"), null)).thenReturn(List.of(up("game", 0)));

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining("The game server is down")
                    .withMessageContaining(GAME_METRICS.toString());
        }

        @Test
        void checkReady_shouldFailWithTheCause_whenPrometheusCannotBeQueried() {
            stubReady();
            when(client.url()).thenReturn(PROMETHEUS);
            final ResourceAccessException cause = new ResourceAccessException("Connection reset");
            when(client.scrapeIntervals()).thenThrow(cause);

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining("Prometheus at http://127.0.0.1:9090 could not be queried")
                    .withCause(cause);
        }

        @Test
        void checkReady_shouldReturnTheStartTimeOfEachServer_whenEveryTargetIsUp() {
            stubReady();

            final Map<String, Instant> actual = meters.checkReady();

            assertThat(actual).isEqualTo(Map.of("game", GAME_STARTED_AT, "login", LOGIN_STARTED_AT));
        }

        @Test
        void checkReady_shouldTakeTheEarliestStart_whenAJobRunsSeveralProcesses() {
            stubReady();
            when(client.query(ServerMeterQuery.processStart("game"), null))
                    .thenReturn(List.of(scrapedAt(GAME_STARTED_AT), scrapedAt(GAME_STARTED_AT.minusSeconds(60))));

            final Map<String, Instant> actual = meters.checkReady();

            assertThat(actual.get("game")).isEqualTo(GAME_STARTED_AT.minusSeconds(60));
        }

        @Test
        void checkReady_shouldFail_whenAServerExposesNoStartTime() {
            stubReady();
            when(client.query(ServerMeterQuery.processStart("login"), null)).thenReturn(List.of());

            assertThatIllegalStateException()
                    .isThrownBy(meters::checkReady)
                    .withMessageContaining("The login server exposes no process_start_time_seconds")
                    .withMessageContaining(LOGIN_METRICS.toString());
        }
    }

    @Nested
    class Finish {
        @Test
        void finish_shouldQueryEveryMeterAtTheWindowEnd_whenTheEndIsIngested() throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null))
                    .thenReturn(List.of(scrapedAt(END.minusMillis(750))))
                    .thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenReturn(List.of());
            stubUp(1, 1);

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual.loops()).isEmpty();
            verify(client, times(2)).query(LAST_SCRAPE, null);
            for (ServerMeterQuery query : ServerMeterQuery.values()) {
                verify(client).query(query.expression(RANGE_MILLIS), END);
            }
        }

        @Test
        void finish_shouldReadNoMeters_whenTheEndIsNotIngestedBeforeTheClockPassesTheWait()
                throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null)).thenAnswer(invocation -> {
                now.updateAndGet(time -> time.plus(POLL_TIME));
                return List.of(scrapedAt(END.minusMillis(1)));
            });

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual).isNull();
            verify(client, times(3).description("polls at 40, 80 and 120 ms of a 100 ms wait"))
                    .query(LAST_SCRAPE, null);
            verify(client, never()).query(anyString(), eq(END));
        }

        @Test
        void finish_shouldKeepWaiting_whenTheLastScrapeQueryFailsTransiently() throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null))
                    .thenThrow(new ResourceAccessException("Connection reset"))
                    .thenThrow(HttpServerErrorException.create(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Service Unavailable",
                            new HttpHeaders(),
                            new byte[0],
                            StandardCharsets.UTF_8))
                    .thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenReturn(List.of());
            stubUp(1, 1);

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual).isNotNull();
            verify(client, times(3)).query(LAST_SCRAPE, null);
        }

        @Test
        void finish_shouldStopWaitingAtOnce_whenPrometheusRefusesTheLastScrapeQuery() throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null))
                    .thenThrow(new IllegalStateException("Prometheus refused " + LAST_SCRAPE + ": bad_data"));

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual).isNull();
            verify(client, times(1)).query(LAST_SCRAPE, null);
            verify(client, never()).query(anyString(), eq(END));
        }

        @Test
        void finish_shouldReadNoMeters_whenAQueryFails() throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null)).thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenThrow(new IllegalStateException("Prometheus refused"));

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual).isNull();
        }

        @ParameterizedTest(name = "game up {0}, login up {1}")
        @CsvSource({"1,0", "0.5,1", "1,-1"})
        void finish_shouldReadNoMeters_whenATargetWasDownInTheWindow(double game, double login)
                throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null)).thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenReturn(List.of());
            stubUp(game, login);

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual).isNull();
            verify(client, never()).query(ServerMeterQuery.PASS_COUNT.expression(RANGE_MILLIS), END);
        }

        @Test
        void finish_shouldReadNoMeters_whenATargetHasNoUpSeries() throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null)).thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenReturn(List.of());
            when(client.query(upThroughout("game"), END)).thenReturn(List.of(up("game", 1)));

            final ServerStats actual = meters.finish(WINDOW);

            assertThat(actual).isNull();
            verify(client, never()).query(ServerMeterQuery.PASS_COUNT.expression(RANGE_MILLIS), END);
        }

        @Test
        void finish_shouldQueryUpOverTheRangePlusEachTargetsScrapeInterval() throws InterruptedException {
            ready();
            when(client.query(LAST_SCRAPE, null)).thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenReturn(List.of());
            stubUp(1, 1);

            meters.finish(WINDOW);

            verify(client).query("min_over_time(up{job=\"game\"}[121500ms])", END);
            verify(client).query("min_over_time(up{job=\"login\"}[121000ms])", END);
        }

        @Test
        void finish_shouldFail_whenCheckReadyHasNotPassed() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> meters.finish(WINDOW))
                    .withMessageContaining("checkReady");
            verify(client, never()).query(any(), any());
        }
    }

    @Nested
    class Snapshots {
        @Test
        void snapshots_shouldKeepEveryTakenTextByJobAndBoundary_whenOneFails() throws InterruptedException {
            ready();
            when(client.text(GAME_METRICS)).thenReturn("game start\n", "game end\n");
            when(client.text(LOGIN_METRICS))
                    .thenThrow(new ResourceAccessException("Connection refused"))
                    .thenReturn("login end\n");
            when(client.query(any(), isNull())).thenReturn(List.of(scrapedAt(END)));
            when(client.query(anyString(), eq(END))).thenReturn(List.of());
            stubUp(1, 1);

            meters.start();
            meters.finish(WINDOW);

            assertThat(meters.snapshots())
                    .isEqualTo(Map.of(
                            "server-metrics-game-start.prom", "game start\n",
                            "server-metrics-game-end.prom", "game end\n",
                            "server-metrics-login-end.prom", "login end\n"));
        }
    }

    private void stubReady() {
        when(client.ready()).thenReturn(true);
        when(client.query(ServerMeterQuery.now(), null)).thenReturn(List.of(scrapedAt(NOW)));
        when(client.scrapeIntervals()).thenReturn(SCRAPE_INTERVALS);
        when(client.query(ServerMeterQuery.up("game"), null)).thenReturn(List.of(up("game", 1)));
        when(client.query(ServerMeterQuery.up("login"), null)).thenReturn(List.of(up("login", 1)));
        when(client.query(ServerMeterQuery.processStart("game"), null)).thenReturn(List.of(scrapedAt(GAME_STARTED_AT)));
        when(client.query(ServerMeterQuery.processStart("login"), null))
                .thenReturn(List.of(scrapedAt(LOGIN_STARTED_AT)));
    }

    private void ready() {
        stubReady();
        meters.checkReady();
    }

    private void stubUp(double game, double login) {
        when(client.query(upThroughout("game"), END)).thenReturn(List.of(up("game", game)));
        when(client.query(upThroughout("login"), END)).thenReturn(List.of(up("login", login)));
    }

    private static String upThroughout(String job) {
        return ServerMeterQuery.upThroughout(job, RANGE_MILLIS, SCRAPE_INTERVALS.get(job));
    }

    private static Sample up(String job, double value) {
        return Sample.builder().labels(Map.of("job", job)).value(value).build();
    }

    private static Sample scrapedAt(Instant time) {
        return Sample.builder()
                .labels(Map.of())
                .value(time.toEpochMilli() / 1_000.0)
                .build();
    }
}
