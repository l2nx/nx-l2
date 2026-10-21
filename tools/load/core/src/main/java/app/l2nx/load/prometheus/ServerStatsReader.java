/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import app.l2nx.load.prometheus.MetricWindow.HistogramWindow;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.ServerStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the server meters the report keeps from a window, by their Prometheus names as Micrometer exposes them:
 * seconds as the base unit of timers, {@code _total} on counters, the timer maximum as its own {@code _max} gauge.
 */
public final class ServerStatsReader {
    static final String PASS = "l2nx_engine_pass_seconds";
    static final String PASS_PHASE = "l2nx_engine_pass_phase_seconds";
    static final String PASS_OVER_BUDGET = "l2nx_engine_pass_over_budget_total";
    static final String FAILURES = "l2nx_engine_failures_total";
    static final String TIMERS_OVERDUE = "l2nx_engine_timers_overdue_total";
    static final String INBOX_DEPTH = "l2nx_engine_inbox_depth";
    static final String SESSION_QUEUE_DEPTH = "l2nx_session_queue_depth";
    static final String LEGACY_TASK = "l2nx_legacy_task_seconds";
    static final String LEGACY_TASK_ALL = "l2nx_legacy_task_all_seconds";
    static final String LEGACY_TASK_LAG = "l2nx_legacy_task_lag_seconds";
    static final String LEGACY_PACKET = "l2nx_legacy_packet_seconds";
    static final String GC_PAUSE = "jvm_gc_pause_seconds";
    static final String PROCESS_CPU = "process_cpu_usage";
    static final String THREADS_LIVE = "jvm_threads_live_threads";

    private static final String LOOP = "loop";
    private static final String PHASE = "phase";
    private static final double MILLIS_PER_SECOND = 1_000.0;
    private static final double P99 = 0.99;

    // The tags that tell one task or packet type from another; outcome and exception are summed.
    private static final Map<String, List<String>> LEGACY_TYPES = Map.of(
            LEGACY_TASK, List.of("pool", "code_namespace"),
            LEGACY_TASK_ALL, List.of(),
            LEGACY_TASK_LAG, List.of("pool"),
            LEGACY_PACKET, List.of("type"));
    // The legacy timers the server registers with a percentile histogram; the others are summaries.
    private static final Set<String> LEGACY_HISTOGRAMS = Set.of(LEGACY_TASK_ALL, LEGACY_TASK_LAG);

    private ServerStatsReader() {}

    /**
     * The server meters of the window. A value is {@code null} when the server does not expose its meter or the window
     * holds no observation of it (a quantile, mean or max of nothing); such entries are left out of the maps.
     *
     * @throws IllegalStateException when a loop's pass histogram comes without the counters the engine registers with
     *     it: a server whose meters this reader does not know
     */
    public static ServerStats read(MetricWindow window) {
        final HistogramWindow sessionQueueDepth = window.histogram(SESSION_QUEUE_DEPTH);
        final HistogramWindow gcPause = window.histogram(GC_PAUSE);
        final ServerStats.ServerStatsBuilder stats = ServerStats.builder()
                .loops(loops(window))
                .sessionQueueDepthP99(at(read(sessionQueueDepth, depth -> depth.quantile(P99)), Map.of()))
                .sessionQueueDepthMax(at(read(sessionQueueDepth, HistogramWindow::max), Map.of()))
                .gcPauseP99Millis(millis(at(read(gcPause, pause -> pause.quantile(P99)), Map.of())))
                .gcPauseMeanMillis(millis(at(read(gcPause, HistogramWindow::mean), Map.of())))
                .gcPauseMaxMillis(millis(at(read(gcPause, HistogramWindow::max), Map.of())))
                .processCpuMean(at(window.sampleMean(PROCESS_CPU), Map.of()))
                .threadsLiveMax(at(window.sampleMax(THREADS_LIVE), Map.of()));
        legacy(window, stats);
        return stats.build();
    }

    private static Map<String, LoopStats> loops(MetricWindow window) {
        final HistogramWindow pass = window.histogram(PASS, LOOP);
        if (pass == null) {
            return Map.of();
        }
        final Map<Map<String, String>, Double> p50 = pass.quantile(0.5);
        final Map<Map<String, String>, Double> p99 = pass.quantile(P99);
        final Map<Map<String, String>, Double> max = pass.max();
        final Map<Map<String, String>, Double> overBudget = window.increase(PASS_OVER_BUDGET, LOOP);
        final Map<Map<String, String>, Double> failures = window.increase(FAILURES, LOOP);
        final Map<Map<String, String>, Double> overdue = window.increase(TIMERS_OVERDUE, LOOP);
        final Map<Map<String, String>, Double> phaseMeans =
                read(window.histogram(PASS_PHASE, LOOP, PHASE), HistogramWindow::mean);
        final Map<Map<String, String>, Double> inboxMax = window.sampleMax(INBOX_DEPTH, LOOP);
        final Map<Map<String, String>, Double> inboxP99 = window.sampleQuantile(P99, INBOX_DEPTH, LOOP);
        final Map<String, LoopStats> loops = new TreeMap<>();
        pass.count().forEach((group, count) -> {
            final String loop = group.get(LOOP);
            if (loop == null) {
                return;
            }
            loops.put(
                    loop,
                    LoopStats.builder()
                            .passes(Math.round(count))
                            .passP50Millis(millis(at(p50, group)))
                            .passP99Millis(millis(at(p99, group)))
                            .passMaxMillis(millis(at(max, group)))
                            .overBudget(required(overBudget, group, PASS_OVER_BUDGET))
                            .failures(required(failures, group, FAILURES))
                            .timersOverdue(required(overdue, group, TIMERS_OVERDUE))
                            .phaseMeanMillis(phaseMeanMillis(phaseMeans, loop))
                            .inboxDepthMax(at(inboxMax, group))
                            .inboxDepthP99(at(inboxP99, group))
                            .build());
        });
        return loops;
    }

    private static Map<String, Double> phaseMeanMillis(Map<Map<String, String>, Double> means, String loop) {
        if (means == null) {
            return null;
        }
        final Map<String, Double> phases = new TreeMap<>();
        means.forEach((group, mean) -> {
            if (loop.equals(group.get(LOOP)) && group.get(PHASE) != null && !mean.isNaN()) {
                phases.put(group.get(PHASE), millis(mean));
            }
        });
        return phases;
    }

    /**
     * Mean of every legacy timer; p99 and max only of those with a histogram, since a summary's {@code _max} is a
     * decaying maximum that still holds observations from before the window and no bucket bounds it.
     */
    private static void legacy(MetricWindow window, ServerStats.ServerStatsBuilder stats) {
        Map<String, Double> means = null;
        Map<String, Double> p99s = null;
        Map<String, Double> maxima = null;
        for (Map.Entry<String, List<String>> legacy : LEGACY_TYPES.entrySet()) {
            final String family = legacy.getKey();
            final HistogramWindow timer =
                    window.histogram(family, legacy.getValue().toArray(String[]::new));
            if (timer == null) {
                continue;
            }
            means = putMillis(means, family, timer.mean());
            if (LEGACY_HISTOGRAMS.contains(family)) {
                final Map<Map<String, String>, Double> p99 = timer.quantile(P99);
                p99s = putMillis(p99s, family, p99);
                final Map<Map<String, String>, Double> max = timer.max();
                // Only a group with buckets has a quantile, and only the buckets bound its max.
                final Map<Map<String, String>, Double> bounded = new LinkedHashMap<>();
                if (max != null) {
                    max.forEach((group, value) -> {
                        if (p99.containsKey(group)) {
                            bounded.put(group, value);
                        }
                    });
                }
                maxima = putMillis(maxima, family, bounded);
            }
        }
        stats.legacyMeanMillis(means).legacyP99Millis(p99s).legacyMaxMillis(maxima);
    }

    private static Map<String, Double> putMillis(
            Map<String, Double> target, String family, Map<Map<String, String>, Double> values) {
        final Map<String, Double> millis = target == null ? new TreeMap<>() : target;
        values.forEach((group, value) -> {
            if (!value.isNaN()) {
                millis.put(selector(family, group), millis(value));
            }
        });
        return millis;
    }

    /** The series as a PromQL selector, such as {@code l2nx_legacy_task_lag_seconds{pool="scheduled"}}. */
    static String selector(String family, Map<String, String> labels) {
        if (labels.isEmpty()) {
            return family;
        }
        return labels.entrySet().stream()
                .map(label -> label.getKey() + "=\"" + escape(label.getValue()) + "\"")
                .collect(Collectors.joining(",", family + "{", "}"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static long required(Map<Map<String, String>, Double> values, Map<String, String> group, String family) {
        final Double value = at(values, group);
        if (value == null) {
            throw new IllegalStateException("Loop " + group.get(LOOP) + " exposes " + PASS + " but not " + family);
        }
        return Math.round(value);
    }

    // NaN is how the window says "no observations": the report has no value then, not a number.
    private static Double at(Map<Map<String, String>, Double> values, Map<String, String> group) {
        final Double value = values == null ? null : values.get(group);
        return value == null || value.isNaN() ? null : value;
    }

    private static Double millis(Double seconds) {
        return seconds == null ? null : seconds * MILLIS_PER_SECOND;
    }

    private static Map<Map<String, String>, Double> read(
            HistogramWindow histogram, Function<HistogramWindow, Map<Map<String, String>, Double>> reading) {
        return histogram == null ? null : reading.apply(histogram);
    }
}
