/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import static app.l2nx.load.prometheus.MetricFamilies.counterMetric;
import static app.l2nx.load.prometheus.MetricFamilies.family;
import static app.l2nx.load.prometheus.MetricFamilies.gauge;
import static app.l2nx.load.prometheus.MetricFamilies.histogramMetric;
import static app.l2nx.load.prometheus.MetricFamilies.scrape;
import static app.l2nx.load.prometheus.MetricFamilies.summaryMetric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.ServerStats;
import io.prometheus.client.Metrics.MetricFamily;
import io.prometheus.client.Metrics.MetricType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerStatsReaderTest {
    private static final Comparator<Double> CLOSE =
            (actual, expected) -> Math.abs(actual - expected) < 1e-9 ? 0 : Double.compare(actual, expected);
    private static final double[] PASS_BOUNDS = {0.001, 0.002, 0.005, 0.01};
    private static final String LAG_SELECTOR = "l2nx_legacy_task_lag_seconds{pool=\"scheduled\"}";
    private static final String TASK_ALL_SELECTOR = "l2nx_legacy_task_all_seconds";
    private static final String TASK_SELECTOR = "l2nx_legacy_task_seconds{code_namespace=\"Task\",pool=\"general\"}";

    @Nested
    class Read {
        @Test
        void read_shouldTakeEverySpecMeter_whenTheServerExposesThem() {
            final MetricWindow window = new MetricWindow(List.of(
                    scrape(
                            pass(new long[] {0, 0, 0, 0}, 0, 0),
                            gauge(ServerStatsReader.PASS + "_max", 0, "loop", "world"),
                            counter(ServerStatsReader.PASS_OVER_BUDGET, 2),
                            failures(0, 1),
                            counter(ServerStatsReader.TIMERS_OVERDUE, 0),
                            phases(0, 0, 0, 0),
                            gauge(ServerStatsReader.INBOX_DEPTH, 3, "loop", "world"),
                            lag(new long[] {0, 0}, 0, 0),
                            gauge(ServerStatsReader.LEGACY_TASK_LAG + "_max", 0, "pool", "scheduled"),
                            taskAll(new long[] {0, 0}, 0, 0),
                            gauge(ServerStatsReader.LEGACY_TASK_ALL + "_max", 0.02),
                            legacyTask(1, 0.001),
                            gauge(
                                    ServerStatsReader.LEGACY_TASK + "_max",
                                    0.002,
                                    "pool",
                                    "general",
                                    "code_namespace",
                                    "Task"),
                            gcPause(new long[] {0, 0}, 0, 0),
                            gauge(ServerStatsReader.GC_PAUSE + "_max", 0),
                            gauge(ServerStatsReader.PROCESS_CPU, 0.2),
                            gauge(ServerStatsReader.THREADS_LIVE, 20)),
                    scrape(
                            pass(new long[] {50, 90, 99, 100}, 100, 0.2),
                            gauge(ServerStatsReader.PASS + "_max", 0.008, "loop", "world"),
                            counter(ServerStatsReader.PASS_OVER_BUDGET, 5),
                            failures(1, 2),
                            counter(ServerStatsReader.TIMERS_OVERDUE, 4),
                            phases(100, 0.05, 100, 0.1),
                            gauge(ServerStatsReader.INBOX_DEPTH, 7, "loop", "world"),
                            lag(new long[] {4, 10}, 10, 0.02),
                            gauge(ServerStatsReader.LEGACY_TASK_LAG + "_max", 0.004, "pool", "scheduled"),
                            taskAll(new long[] {2, 4}, 4, 0.012),
                            gauge(ServerStatsReader.LEGACY_TASK_ALL + "_max", 0.003),
                            legacyTask(3, 0.007),
                            gauge(
                                    ServerStatsReader.LEGACY_TASK + "_max",
                                    0.005,
                                    "pool",
                                    "general",
                                    "code_namespace",
                                    "Task"),
                            gcPause(new long[] {1, 2}, 2, 0.06),
                            gauge(ServerStatsReader.GC_PAUSE + "_max", 0.05),
                            gauge(ServerStatsReader.PROCESS_CPU, 0.4),
                            gauge(ServerStatsReader.THREADS_LIVE, 25))));

            final ServerStats actual = ServerStatsReader.read(window);

            final ServerStats expected = ServerStats.builder()
                    .loops(Map.of(
                            "world",
                            LoopStats.builder()
                                    .passes(100)
                                    .passP50Millis(1.0)
                                    .passP99Millis(5.0)
                                    .passMaxMillis(8.0)
                                    .overBudget(3)
                                    .failures(2)
                                    .timersOverdue(4)
                                    .phaseMeanMillis(Map.of("inbox", 0.5, "timers", 1.0))
                                    .inboxDepthMax(7.0)
                                    .inboxDepthP99(6.96)
                                    .build()))
                    .legacyMeanMillis(Map.of(LAG_SELECTOR, 2.0, TASK_ALL_SELECTOR, 3.0, TASK_SELECTOR, 3.0))
                    // The summary-only task timer has neither p99 nor max; the task.all max is bounded by its buckets.
                    .legacyP99Millis(Map.of(LAG_SELECTOR, 9.85, TASK_ALL_SELECTOR, 9.82))
                    .legacyMaxMillis(Map.of(LAG_SELECTOR, 4.0, TASK_ALL_SELECTOR, 10.0))
                    .gcPauseP99Millis(98.2)
                    .gcPauseMeanMillis(30.0)
                    .gcPauseMaxMillis(50.0)
                    .processCpuMean(0.3)
                    .threadsLiveMax(25.0)
                    .build();
            assertThat(actual)
                    .usingRecursiveComparison()
                    .withComparatorForType(CLOSE, Double.class)
                    .isEqualTo(expected);
        }

        @Test
        void read_shouldLeaveMetersNull_whenTheServerDoesNotExposeThem() {
            final MetricWindow window = new MetricWindow(List.of(
                    scrape(gauge(ServerStatsReader.PROCESS_CPU, 0.5)),
                    scrape(gauge(ServerStatsReader.PROCESS_CPU, 0.5))));

            final ServerStats actual = ServerStatsReader.read(window);

            assertThat(actual)
                    .isEqualTo(ServerStats.builder()
                            .loops(Map.of())
                            .processCpuMean(0.5)
                            .build());
        }

        @Test
        void read_shouldLeavePassValuesNull_whenTheWindowHoldsNoPass() {
            final List<MetricFamily> idle = List.of(
                    pass(new long[] {1, 1, 1, 1}, 1, 0.001),
                    gauge(ServerStatsReader.PASS + "_max", 0.001, "loop", "world"),
                    counter(ServerStatsReader.PASS_OVER_BUDGET, 0),
                    failures(0, 0),
                    counter(ServerStatsReader.TIMERS_OVERDUE, 0));
            final MetricWindow window = new MetricWindow(
                    List.of(scrape(idle.toArray(MetricFamily[]::new)), scrape(idle.toArray(MetricFamily[]::new))));

            final LoopStats actual = ServerStatsReader.read(window).loops().get("world");

            assertThat(actual).isEqualTo(LoopStats.builder().passes(0).build());
        }

        @Test
        void read_shouldFail_whenALoopLacksTheCountersRegisteredWithItsPasses() {
            final MetricWindow window = new MetricWindow(List.of(
                    scrape(pass(new long[] {0, 0, 0, 0}, 0, 0)), scrape(pass(new long[] {1, 1, 1, 1}, 1, 0.001))));

            assertThatIllegalStateException()
                    .isThrownBy(() -> ServerStatsReader.read(window))
                    .withMessageContaining(ServerStatsReader.PASS_OVER_BUDGET);
        }
    }

    private static MetricFamily pass(long[] cumulative, long count, double sum) {
        return family(
                ServerStatsReader.PASS,
                MetricType.HISTOGRAM,
                histogramMetric(PASS_BOUNDS, cumulative, count, sum, "loop", "world"));
    }

    private static MetricFamily counter(String name, double value) {
        return MetricFamilies.counter(name, value, "loop", "world");
    }

    private static MetricFamily failures(double inbox, double timers) {
        return family(
                ServerStatsReader.FAILURES,
                MetricType.COUNTER,
                counterMetric(inbox, "loop", "world", "phase", "inbox"),
                counterMetric(timers, "loop", "world", "phase", "timers"));
    }

    private static MetricFamily phases(long inboxCount, double inboxSum, long timersCount, double timersSum) {
        return family(
                ServerStatsReader.PASS_PHASE,
                MetricType.SUMMARY,
                summaryMetric(inboxCount, inboxSum, "loop", "world", "phase", "inbox"),
                summaryMetric(timersCount, timersSum, "loop", "world", "phase", "timers"));
    }

    private static MetricFamily lag(long[] cumulative, long count, double sum) {
        return family(
                ServerStatsReader.LEGACY_TASK_LAG,
                MetricType.HISTOGRAM,
                histogramMetric(new double[] {0.001, 0.01}, cumulative, count, sum, "pool", "scheduled"));
    }

    private static MetricFamily taskAll(long[] cumulative, long count, double sum) {
        return family(
                ServerStatsReader.LEGACY_TASK_ALL,
                MetricType.HISTOGRAM,
                histogramMetric(new double[] {0.001, 0.01}, cumulative, count, sum));
    }

    private static MetricFamily legacyTask(long count, double sum) {
        return family(
                ServerStatsReader.LEGACY_TASK,
                MetricType.SUMMARY,
                summaryMetric(count, sum, "pool", "general", "code_namespace", "Task"));
    }

    private static MetricFamily gcPause(long[] cumulative, long count, double sum) {
        return family(
                ServerStatsReader.GC_PAUSE,
                MetricType.HISTOGRAM,
                histogramMetric(new double[] {0.01, 0.1}, cumulative, count, sum, "gc", "G1 Young Generation"));
    }
}
