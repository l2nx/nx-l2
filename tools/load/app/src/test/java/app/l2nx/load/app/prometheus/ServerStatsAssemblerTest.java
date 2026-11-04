/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.ProcessStats;
import app.l2nx.load.report.model.ServerStats;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerStatsAssemblerTest {
    private static final String WORLD = "world-0";
    private static final String DRAIN = "l2nx-game-packets";

    @Nested
    class Loops {
        @Test
        void assemble_shouldGiveEveryLoopWithAPassTimerItsValuesInMillis() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.loops()).containsOnlyKeys(WORLD, "world-1", DRAIN);
            assertThat(actual.loops().get(WORLD))
                    .isEqualTo(LoopStats.builder()
                            .passes(6_000)
                            .passP50Millis(5.0)
                            .passP99Millis(20.0)
                            .passMaxMillis(40.0)
                            .overBudget(3)
                            .failures(1)
                            .timersOverdue(2)
                            .ownershipViolations(1)
                            .jdbcOnLoop(4)
                            .jdbcOnLoopSeconds(0.25)
                            .jdbcOnLoopShare(0.02)
                            .phaseMeanMillis(Map.of("input", 0.5, "tick", 2.5))
                            .inboxDepthMax(12.0)
                            .inboxDepthP99(4.0)
                            .build());
        }

        @Test
        void assemble_shouldReportTheFailuresOfALoopWithoutAPassTimer_whenOnlyItsFailureSeriesExists() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();

            final LoopStats actual =
                    ServerStatsAssembler.assemble(results).loops().get(DRAIN);

            assertThat(actual)
                    .isEqualTo(LoopStats.builder()
                            .untimed(true)
                            .failures(2)
                            .phaseMeanMillis(Map.of())
                            .build());
        }

        @Test
        void assemble_shouldLeaveOutAQuantileOfNothing_whenTheWindowHoldsNoPass() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();

            final LoopStats actual =
                    ServerStatsAssembler.assemble(results).loops().get("world-1");

            assertThat(actual.passes()).isZero();
            assertThat(actual.passP50Millis()).isNull();
            assertThat(actual.passP99Millis()).isNull();
            assertThat(actual.passMaxMillis())
                    .as("the gauge still holds a pass from before the window")
                    .isNull();
            assertThat(actual.phaseMeanMillis()).isEmpty();
        }

        @Test
        void assemble_shouldCapTheMaxAtTheHighestBucketTheWindowFilled_whenTheGaugeHoldsAnEarlierPass() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();
            results.put(ServerMeterQuery.PASS_MAX, List.of(loop(WORLD, 0.3), loop("world-1", 0.001)));

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.loops().get(WORLD).passMaxMillis()).isEqualTo(50.0);
        }

        @Test
        void assemble_shouldGiveNoMax_whenTheLoopHasNoBucketsToBoundIt() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();
            results.remove(ServerMeterQuery.PASS_BUCKETS);

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.loops().get(WORLD).passMaxMillis()).isNull();
        }

        @Test
        void assemble_shouldCountNoJdbcCall_whenTheLoopHasNoJdbcSeries() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();
            results.remove(ServerMeterQuery.JDBC_ON_LOOP);
            results.remove(ServerMeterQuery.JDBC_ON_LOOP_SECONDS);
            results.remove(ServerMeterQuery.JDBC_ON_LOOP_SHARE);

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.loops().get(WORLD).jdbcOnLoop()).isZero();
            assertThat(actual.loops().get(WORLD).jdbcOnLoopSeconds()).isNull();
            assertThat(actual.loops().get(WORLD).jdbcOnLoopShare()).isNull();
        }

        @Test
        void assemble_shouldKeepAnInfiniteQuantile_whenPassesExceedTheTopBucket() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();
            results.put(ServerMeterQuery.PASS_P99, List.of(loop(WORLD, Double.POSITIVE_INFINITY)));

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.loops().get(WORLD).passP99Millis()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        void assemble_shouldFail_whenALoopHasPassesButNoOwnershipCounter() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();
            results.remove(ServerMeterQuery.OWNERSHIP_VIOLATIONS);

            assertThatIllegalStateException()
                    .isThrownBy(() -> ServerStatsAssembler.assemble(results))
                    .withMessageContaining(ServerMeterQuery.OWNERSHIP_VIOLATIONS.name());
        }

        @Test
        void assemble_shouldFail_whenALoopHasPassesButNoOverBudgetCounter() {
            final Map<ServerMeterQuery, List<Sample>> results = loopResults();
            results.put(ServerMeterQuery.OVER_BUDGET, List.of(loop("world-1", 0)));

            assertThatIllegalStateException()
                    .isThrownBy(() -> ServerStatsAssembler.assemble(results))
                    .withMessageContaining(WORLD)
                    .withMessageContaining(ServerMeterQuery.OVER_BUDGET.name());
        }
    }

    @Nested
    class Server {
        @Test
        void assemble_shouldKeyProcessesByJob() {
            final Map<ServerMeterQuery, List<Sample>> results = new EnumMap<>(ServerMeterQuery.class);
            results.put(ServerMeterQuery.GC_PAUSE_P99, List.of(job("game", 0.003), job("login", Double.NaN)));
            results.put(ServerMeterQuery.GC_PAUSE_MEAN, List.of(job("game", 0.0012)));
            results.put(
                    ServerMeterQuery.GC_PAUSE_BUCKETS,
                    List.of(
                            bucket(Map.of("job", "game"), "0.002", 1),
                            bucket(Map.of("job", "game"), "0.005", 2),
                            bucket(Map.of("job", "game"), "+Inf", 2),
                            bucket(Map.of("job", "login"), "0.002", 0),
                            bucket(Map.of("job", "login"), "+Inf", 0)));
            results.put(ServerMeterQuery.GC_PAUSE_MAX, List.of(job("game", 0.004), job("login", 0.2)));
            results.put(ServerMeterQuery.CPU_MEAN, List.of(job("game", 0.35), job("login", 0.05)));
            results.put(ServerMeterQuery.THREADS_LIVE_MAX, List.of(job("game", 64), job("login", 31)));

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.processes())
                    .isEqualTo(Map.of(
                            "game",
                            ProcessStats.builder()
                                    .gcPauseP99Millis(3.0)
                                    .gcPauseMeanMillis(1.2)
                                    .gcPauseMaxMillis(4.0)
                                    .cpuMean(0.35)
                                    .threadsLiveMax(64.0)
                                    .build(),
                            "login",
                            ProcessStats.builder()
                                    .cpuMean(0.05)
                                    .threadsLiveMax(31.0)
                                    .build()));
        }

        @Test
        void assemble_shouldLeaveOutALegacyPacketType_whenItsMeanIsOfNothing() {
            final Map<ServerMeterQuery, List<Sample>> results = new EnumMap<>(ServerMeterQuery.class);
            results.put(
                    ServerMeterQuery.LEGACY_PACKET_MEAN,
                    List.of(
                            sample(Map.of("type", "MoveBackwardToLocation"), 0.0001),
                            sample(Map.of("type", "Say2"), Double.NaN)));
            results.put(ServerMeterQuery.SESSION_QUEUE_DEPTH_P99, List.of(sample(Map.of(), 3)));
            results.put(
                    ServerMeterQuery.SESSION_QUEUE_DEPTH_BUCKETS,
                    List.of(
                            bucket(Map.of(), "4.0", 40),
                            bucket(Map.of(), "16.0", 50),
                            bucket(Map.of(), "64.0", 50),
                            bucket(Map.of(), "+Inf", 50)));
            results.put(ServerMeterQuery.SESSION_QUEUE_DEPTH_MAX, List.of(sample(Map.of(), 9)));

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.legacyPacketMeanMillis()).isEqualTo(Map.of("MoveBackwardToLocation", 0.1));
            assertThat(actual.sessionQueueDepthP99()).isEqualTo(3.0);
            assertThat(actual.sessionQueueDepthMax()).isEqualTo(9.0);
        }

        @Test
        void assemble_shouldCapTheSessionQueueDepthMax_whenTheGaugeHoldsAnEarlierDepth() {
            final Map<ServerMeterQuery, List<Sample>> results = new EnumMap<>(ServerMeterQuery.class);
            results.put(
                    ServerMeterQuery.SESSION_QUEUE_DEPTH_BUCKETS,
                    List.of(bucket(Map.of(), "4.0", 40), bucket(Map.of(), "16.0", 40), bucket(Map.of(), "+Inf", 40)));
            results.put(ServerMeterQuery.SESSION_QUEUE_DEPTH_MAX, List.of(sample(Map.of(), 120)));

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.sessionQueueDepthMax()).isEqualTo(4.0);
        }

        @Test
        void assemble_shouldKeepAMaxAboveTheTopBound_whenTheWindowFilledTheInfiniteBucket() {
            final Map<ServerMeterQuery, List<Sample>> results = new EnumMap<>(ServerMeterQuery.class);
            results.put(
                    ServerMeterQuery.SESSION_QUEUE_DEPTH_BUCKETS,
                    List.of(bucket(Map.of(), "64.0", 39), bucket(Map.of(), "+Inf", 40)));
            results.put(ServerMeterQuery.SESSION_QUEUE_DEPTH_MAX, List.of(sample(Map.of(), 120)));

            final ServerStats actual = ServerStatsAssembler.assemble(results);

            assertThat(actual.sessionQueueDepthMax()).isEqualTo(120.0);
        }

        @Test
        void assemble_shouldGiveEmptyMapsAndNoValues_whenEveryResultIsEmpty() {
            final ServerStats actual = ServerStatsAssembler.assemble(Map.of());

            assertThat(actual)
                    .isEqualTo(ServerStats.builder()
                            .loops(Map.of())
                            .legacyPacketMeanMillis(Map.of())
                            .processes(Map.of())
                            .build());
        }
    }

    private static Map<ServerMeterQuery, List<Sample>> loopResults() {
        final Map<ServerMeterQuery, List<Sample>> results = new EnumMap<>(ServerMeterQuery.class);
        results.put(ServerMeterQuery.PASS_COUNT, List.of(loop(WORLD, 5_999.6), loop("world-1", 0)));
        results.put(ServerMeterQuery.PASS_P50, List.of(loop(WORLD, 0.005), loop("world-1", Double.NaN)));
        results.put(ServerMeterQuery.PASS_P99, List.of(loop(WORLD, 0.02), loop("world-1", Double.NaN)));
        results.put(
                ServerMeterQuery.PASS_BUCKETS,
                List.of(
                        passBucket(WORLD, "0.005", 3_000),
                        passBucket(WORLD, "0.025", 5_990),
                        passBucket(WORLD, "0.05", 5_999.6),
                        passBucket(WORLD, "0.1", 5_999.6),
                        passBucket(WORLD, "+Inf", 5_999.6),
                        passBucket("world-1", "0.005", 0),
                        passBucket("world-1", "+Inf", 0)));
        results.put(ServerMeterQuery.PASS_MAX, List.of(loop(WORLD, 0.04), loop("world-1", 0.001)));
        results.put(
                ServerMeterQuery.PHASE_MEAN,
                List.of(
                        phase(WORLD, "input", 0.0005),
                        phase(WORLD, "tick", 0.0025),
                        phase("world-1", "tick", Double.NaN)));
        results.put(ServerMeterQuery.OVER_BUDGET, List.of(loop(WORLD, 3.2), loop("world-1", 0)));
        results.put(ServerMeterQuery.FAILURES, List.of(loop(WORLD, 1), loop("world-1", 0), loop(DRAIN, 2)));
        results.put(ServerMeterQuery.TIMERS_OVERDUE, List.of(loop(WORLD, 1.6), loop("world-1", 0)));
        results.put(ServerMeterQuery.OWNERSHIP_VIOLATIONS, List.of(loop(WORLD, 1), loop("world-1", 0)));
        results.put(ServerMeterQuery.JDBC_ON_LOOP, List.of(loop(WORLD, 4)));
        results.put(ServerMeterQuery.JDBC_ON_LOOP_SECONDS, List.of(loop(WORLD, 0.25)));
        results.put(ServerMeterQuery.JDBC_ON_LOOP_SHARE, List.of(loop(WORLD, 0.02)));
        results.put(ServerMeterQuery.INBOX_DEPTH_MAX, List.of(loop(WORLD, 12), loop("world-1", 0)));
        results.put(ServerMeterQuery.INBOX_DEPTH_P99, List.of(loop(WORLD, 4), loop("world-1", 0)));
        return results;
    }

    private static Sample loop(String loop, double value) {
        return sample(Map.of("loop", loop), value);
    }

    private static Sample passBucket(String loop, String le, double value) {
        return bucket(Map.of("loop", loop), le, value);
    }

    private static Sample bucket(Map<String, String> labels, String le, double value) {
        final Map<String, String> withBound = new HashMap<>(labels);
        withBound.put("le", le);
        return sample(withBound, value);
    }

    private static Sample phase(String loop, String phase, double value) {
        return sample(Map.of("loop", loop, "phase", phase), value);
    }

    private static Sample job(String job, double value) {
        return sample(Map.of("job", job), value);
    }

    private static Sample sample(Map<String, String> labels, double value) {
        return Sample.builder().labels(labels).value(value).build();
    }
}
