/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import app.l2nx.load.app.prometheus.model.QueryResponse;
import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.ProcessStats;
import app.l2nx.load.report.model.ServerStats;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * NaN and a missing series both mean "no value" (entry left out or {@code null}); an infinite quantile, an observation
 * above the top bucket, is kept. Micrometer's decaying {@code _max} gauge holds observations from up to two minutes
 * before the window, so a maximum is capped at the highest bucket that gained observations in it, else {@code null}.
 */
public final class ServerStatsAssembler {
    static final String LOOP = "loop";
    static final String PHASE = "phase";
    static final String TYPE = "type";
    static final String JOB = "job";
    static final String LE = "le";

    private ServerStatsAssembler() {}

    /**
     * @param results a query without an entry counts as an empty result
     * @throws IllegalStateException when a loop exposes passes without the counters the engine registers with them, a
     *     server this harness does not know
     */
    public static ServerStats assemble(Map<ServerMeterQuery, List<Sample>> results) {
        return ServerStats.builder()
                .loops(loops(results))
                .sessionQueueDepthP99(value(results, ServerMeterQuery.SESSION_QUEUE_DEPTH_P99, sample -> true))
                .sessionQueueDepthMax(boundedMax(
                        results,
                        ServerMeterQuery.SESSION_QUEUE_DEPTH_MAX,
                        ServerMeterQuery.SESSION_QUEUE_DEPTH_BUCKETS,
                        sample -> true))
                .legacyPacketMeanMillis(millisBy(results, ServerMeterQuery.LEGACY_PACKET_MEAN, TYPE, sample -> true))
                .processes(processes(results))
                .build();
    }

    private static Map<String, LoopStats> loops(Map<ServerMeterQuery, List<Sample>> results) {
        final Map<String, LoopStats> loops = new TreeMap<>();
        for (Sample count : result(results, ServerMeterQuery.PASS_COUNT)) {
            final String loop = count.labels().get(LOOP);
            if (loop == null) {
                continue;
            }
            final Predicate<Sample> ofLoop =
                    sample -> loop.equals(sample.labels().get(LOOP));
            final Double jdbcOnLoop = value(results, ServerMeterQuery.JDBC_ON_LOOP, ofLoop);
            loops.put(
                    loop,
                    LoopStats.builder()
                            .passes(Math.round(count.value()))
                            .passP50Millis(millis(value(results, ServerMeterQuery.PASS_P50, ofLoop)))
                            .passP99Millis(millis(value(results, ServerMeterQuery.PASS_P99, ofLoop)))
                            .passMaxMillis(millis(boundedMax(
                                    results, ServerMeterQuery.PASS_MAX, ServerMeterQuery.PASS_BUCKETS, ofLoop)))
                            .overBudget(required(results, ServerMeterQuery.OVER_BUDGET, loop, ofLoop))
                            .failures(required(results, ServerMeterQuery.FAILURES, loop, ofLoop))
                            .timersOverdue(required(results, ServerMeterQuery.TIMERS_OVERDUE, loop, ofLoop))
                            // The timer registers on a loop's first JDBC call: no series means no call.
                            .jdbcOnLoop(jdbcOnLoop == null ? 0 : Math.round(jdbcOnLoop))
                            .phaseMeanMillis(millisBy(results, ServerMeterQuery.PHASE_MEAN, PHASE, ofLoop))
                            .inboxDepthMax(value(results, ServerMeterQuery.INBOX_DEPTH_MAX, ofLoop))
                            .inboxDepthP99(value(results, ServerMeterQuery.INBOX_DEPTH_P99, ofLoop))
                            .build());
        }
        return loops;
    }

    private static Map<String, Double> millisBy(
            Map<ServerMeterQuery, List<Sample>> results,
            ServerMeterQuery query,
            String label,
            Predicate<Sample> filter) {
        final Map<String, Double> values = new TreeMap<>();
        for (Sample sample : result(results, query)) {
            final String key = sample.labels().get(label);
            if (key != null && filter.test(sample) && !Double.isNaN(sample.value())) {
                values.put(key, sample.value() * Sample.MILLIS_PER_SECOND);
            }
        }
        return values;
    }

    private static Map<String, ProcessStats> processes(Map<ServerMeterQuery, List<Sample>> results) {
        final Set<String> jobs = new TreeSet<>();
        for (ServerMeterQuery query : List.of(
                ServerMeterQuery.GC_PAUSE_P99,
                ServerMeterQuery.GC_PAUSE_MEAN,
                ServerMeterQuery.GC_PAUSE_MAX,
                ServerMeterQuery.CPU_MEAN,
                ServerMeterQuery.THREADS_LIVE_MAX)) {
            for (Sample sample : result(results, query)) {
                if (sample.labels().get(JOB) != null) {
                    jobs.add(sample.labels().get(JOB));
                }
            }
        }
        final Map<String, ProcessStats> processes = new TreeMap<>();
        for (String job : jobs) {
            final Predicate<Sample> ofJob = sample -> job.equals(sample.labels().get(JOB));
            processes.put(
                    job,
                    ProcessStats.builder()
                            .gcPauseP99Millis(millis(value(results, ServerMeterQuery.GC_PAUSE_P99, ofJob)))
                            .gcPauseMeanMillis(millis(value(results, ServerMeterQuery.GC_PAUSE_MEAN, ofJob)))
                            .gcPauseMaxMillis(millis(boundedMax(
                                    results, ServerMeterQuery.GC_PAUSE_MAX, ServerMeterQuery.GC_PAUSE_BUCKETS, ofJob)))
                            .cpuMean(value(results, ServerMeterQuery.CPU_MEAN, ofJob))
                            .threadsLiveMax(value(results, ServerMeterQuery.THREADS_LIVE_MAX, ofJob))
                            .build());
        }
        return processes;
    }

    private static long required(
            Map<ServerMeterQuery, List<Sample>> results,
            ServerMeterQuery query,
            String loop,
            Predicate<Sample> ofLoop) {
        final Double value = value(results, query, ofLoop);
        if (value == null) {
            throw new IllegalStateException("Loop " + loop + " exposes passes but no " + query + " series");
        }
        return Math.round(value);
    }

    /**
     * The smallest {@code le} whose cumulative count reaches the {@code +Inf} bucket's.
     */
    private static Double boundedMax(
            Map<ServerMeterQuery, List<Sample>> results,
            ServerMeterQuery max,
            ServerMeterQuery buckets,
            Predicate<Sample> filter) {
        final Double gauge = value(results, max, filter);
        if (gauge == null) {
            return null;
        }
        final NavigableMap<Double, Double> cumulative = new TreeMap<>();
        for (Sample bucket : result(results, buckets)) {
            final String bound = bucket.labels().get(LE);
            if (bound != null && filter.test(bucket)) {
                cumulative.merge(QueryResponse.parse(bound), bucket.value(), Double::sum);
            }
        }
        final Double observations = cumulative.get(Double.POSITIVE_INFINITY);
        if (observations == null || observations <= 0) {
            return null;
        }
        final double bound = cumulative.entrySet().stream()
                .filter(bucket -> bucket.getValue() >= observations)
                .findFirst()
                .orElseThrow()
                .getKey();
        return Math.min(gauge, bound);
    }

    /** The value of the first matching series; {@code null} when none matches or it is NaN. */
    private static Double value(
            Map<ServerMeterQuery, List<Sample>> results, ServerMeterQuery query, Predicate<Sample> filter) {
        for (Sample sample : result(results, query)) {
            if (filter.test(sample)) {
                return Double.isNaN(sample.value()) ? null : sample.value();
            }
        }
        return null;
    }

    private static List<Sample> result(Map<ServerMeterQuery, List<Sample>> results, ServerMeterQuery query) {
        return results.getOrDefault(query, List.of());
    }

    private static Double millis(Double seconds) {
        return seconds == null ? null : seconds * Sample.MILLIS_PER_SECOND;
    }
}
