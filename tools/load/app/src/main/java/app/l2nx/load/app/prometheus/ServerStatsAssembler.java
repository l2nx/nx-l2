/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import app.l2nx.load.app.prometheus.model.QueryResponse;
import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.report.model.InputStats;
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
     * @throws IllegalStateException when a loop has a pass timer without the counters the engine registers with it, a
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
                .inputs(inputs(results))
                .build();
    }

    /** The union of the pass and failure series: the session drain counts failures but has no pass timer. */
    private static Map<String, LoopStats> loops(Map<ServerMeterQuery, List<Sample>> results) {
        final Set<String> names = new TreeSet<>();
        for (ServerMeterQuery query : List.of(ServerMeterQuery.PASS_COUNT, ServerMeterQuery.FAILURES)) {
            for (Sample sample : result(results, query)) {
                if (sample.labels().get(LOOP) != null) {
                    names.add(sample.labels().get(LOOP));
                }
            }
        }
        final Map<String, LoopStats> loops = new TreeMap<>();
        for (String loop : names) {
            final Predicate<Sample> ofLoop =
                    sample -> loop.equals(sample.labels().get(LOOP));
            final Double passes = value(results, ServerMeterQuery.PASS_COUNT, ofLoop);
            final boolean timed = passes != null;
            final Double jdbcOnLoop = value(results, ServerMeterQuery.JDBC_ON_LOOP, ofLoop);
            loops.put(
                    loop,
                    LoopStats.builder()
                            .untimed(!timed)
                            .passes(timed ? Math.round(passes) : 0)
                            .passP50Millis(millis(value(results, ServerMeterQuery.PASS_P50, ofLoop)))
                            .passP99Millis(millis(value(results, ServerMeterQuery.PASS_P99, ofLoop)))
                            .passMaxMillis(millis(boundedMax(
                                    results, ServerMeterQuery.PASS_MAX, ServerMeterQuery.PASS_BUCKETS, ofLoop)))
                            .overBudget(count(results, ServerMeterQuery.OVER_BUDGET, loop, ofLoop, timed))
                            .failures(count(results, ServerMeterQuery.FAILURES, loop, ofLoop, timed))
                            .timersOverdue(count(results, ServerMeterQuery.TIMERS_OVERDUE, loop, ofLoop, timed))
                            .ownershipViolations(
                                    count(results, ServerMeterQuery.OWNERSHIP_VIOLATIONS, loop, ofLoop, timed))
                            // The timer registers on a loop's first JDBC call: no series means no call.
                            .jdbcOnLoop(jdbcOnLoop == null ? 0 : Math.round(jdbcOnLoop))
                            .jdbcOnLoopSeconds(value(results, ServerMeterQuery.JDBC_ON_LOOP_SECONDS, ofLoop))
                            .jdbcOnLoopShare(value(results, ServerMeterQuery.JDBC_ON_LOOP_SHARE, ofLoop))
                            .jdbcReads(Math.round(valueOrZero(results, ServerMeterQuery.JDBC_READS_ON_LOOP, ofLoop)))
                            .jdbcWrites(Math.round(valueOrZero(results, ServerMeterQuery.JDBC_WRITES_ON_LOOP, ofLoop)))
                            .phaseMeanMillis(millisBy(results, ServerMeterQuery.PHASE_MEAN, PHASE, ofLoop))
                            .inboxDepthMax(value(results, ServerMeterQuery.INBOX_DEPTH_MAX, ofLoop))
                            .inboxDepthP99(value(results, ServerMeterQuery.INBOX_DEPTH_P99, ofLoop))
                            .busyShare(value(results, ServerMeterQuery.PASS_BUSY_SHARE, ofLoop))
                            .build());
        }
        return loops;
    }

    /** Every outcome counts, since a failed input took its time too. */
    private static Map<String, InputStats> inputs(Map<ServerMeterQuery, List<Sample>> results) {
        final Map<String, InputStats> inputs = new TreeMap<>();
        for (Sample sample : result(results, ServerMeterQuery.INPUT_COUNT)) {
            final String type = sample.labels().get(TYPE);
            if (type == null || Double.isNaN(sample.value()) || Math.round(sample.value()) == 0) {
                continue;
            }
            final Predicate<Sample> ofType =
                    candidate -> type.equals(candidate.labels().get(TYPE));
            inputs.put(
                    type,
                    InputStats.builder()
                            .count(Math.round(sample.value()))
                            .p50Millis(millis(value(results, ServerMeterQuery.INPUT_P50, ofType)))
                            .p99Millis(millis(value(results, ServerMeterQuery.INPUT_P99, ofType)))
                            .maxMillis(millis(boundedMax(
                                    results, ServerMeterQuery.INPUT_MAX, ServerMeterQuery.INPUT_BUCKETS, ofType)))
                            .build());
        }
        return inputs;
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

    private static long count(
            Map<ServerMeterQuery, List<Sample>> results,
            ServerMeterQuery query,
            String loop,
            Predicate<Sample> ofLoop,
            boolean timed) {
        final Double value = value(results, query, ofLoop);
        if (value != null) {
            return Math.round(value);
        }
        if (timed) {
            throw new IllegalStateException("Loop " + loop + " exposes passes but no " + query + " series");
        }
        return 0;
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

    private static double valueOrZero(
            Map<ServerMeterQuery, List<Sample>> results, ServerMeterQuery query, Predicate<Sample> filter) {
        final Double value = value(results, query, filter);
        return value == null ? 0 : value;
    }

    private static List<Sample> result(Map<ServerMeterQuery, List<Sample>> results, ServerMeterQuery query) {
        return results.getOrDefault(query, List.of());
    }

    private static Double millis(Double seconds) {
        return seconds == null ? null : seconds * Sample.MILLIS_PER_SECOND;
    }
}
