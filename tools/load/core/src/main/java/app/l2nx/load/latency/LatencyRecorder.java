/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.latency;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.StepStats;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

/** Records wait-free from every event loop, in nanoseconds from the intended time; read after the run from one thread. */
public final class LatencyRecorder {
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final int SIGNIFICANT_DIGITS = 3;

    private final Map<IntentKind, Recorder> latencies = new EnumMap<>(IntentKind.class);
    private final Map<IntentKind, Histogram> totals = new EnumMap<>(IntentKind.class);
    private final Map<IntentKind, LongAdder> sent = counters();
    private final Map<IntentKind, LongAdder> timeouts = counters();
    private final Map<IntentKind, LongAdder> errors = counters();
    private final Map<LoadBotState, Recorder> steps = new EnumMap<>(LoadBotState.class);
    private final Map<LoadBotState, Histogram> stepTotals = new EnumMap<>(LoadBotState.class);

    public LatencyRecorder() {
        for (IntentKind kind : IntentKind.values()) {
            latencies.put(kind, new Recorder(SIGNIFICANT_DIGITS));
            totals.put(kind, new Histogram(SIGNIFICANT_DIGITS));
        }
        for (LoadBotState state : LoadBotState.values()) {
            steps.put(state, new Recorder(SIGNIFICANT_DIGITS));
            stepTotals.put(state, new Histogram(SIGNIFICANT_DIGITS));
        }
    }

    private static Map<IntentKind, LongAdder> counters() {
        final Map<IntentKind, LongAdder> counters = new EnumMap<>(IntentKind.class);
        for (IntentKind kind : IntentKind.values()) {
            counters.put(kind, new LongAdder());
        }
        return Collections.unmodifiableMap(counters);
    }

    public void recordSent(IntentKind kind) {
        sent.get(kind).increment();
    }

    public void recordAcknowledged(IntentKind kind, long latencyNanos) {
        latencies.get(kind).recordValue(latencyNanos);
    }

    public void recordTimeout(IntentKind kind) {
        timeouts.get(kind).increment();
    }

    public void recordError(IntentKind kind) {
        errors.get(kind).increment();
    }

    public void recordStep(LoadBotState state, long nanos) {
        steps.get(state).recordValue(nanos);
    }

    public Histogram histogram(IntentKind kind) {
        return accumulate(totals.get(kind), latencies.get(kind));
    }

    public Histogram stepHistogram(LoadBotState state) {
        return accumulate(stepTotals.get(state), steps.get(state));
    }

    private static Histogram accumulate(Histogram total, Recorder recorder) {
        total.add(recorder.getIntervalHistogram());
        return total.copy();
    }

    public long sent(IntentKind kind) {
        return sent.get(kind).sum();
    }

    public long timeouts(IntentKind kind) {
        return timeouts.get(kind).sum();
    }

    public long errors(IntentKind kind) {
        return errors.get(kind).sum();
    }

    public Map<IntentKind, IntentStats> stats() {
        final Map<IntentKind, IntentStats> stats = new EnumMap<>(IntentKind.class);
        for (IntentKind kind : IntentKind.values()) {
            final long sentCount = sent(kind);
            if (sentCount == 0) {
                continue;
            }
            final Histogram histogram = histogram(kind);
            stats.put(
                    kind,
                    IntentStats.builder()
                            .sent(sentCount)
                            .acknowledged(histogram.getTotalCount())
                            .timeouts(timeouts(kind))
                            .errors(errors(kind))
                            .p50Millis(percentileMillis(histogram, 50.0))
                            .p99Millis(percentileMillis(histogram, 99.0))
                            .p999Millis(percentileMillis(histogram, 99.9))
                            .maxMillis(histogram.getMaxValue() / NANOS_PER_MILLI)
                            .build());
        }
        return Collections.unmodifiableMap(stats);
    }

    public Map<LoadBotState, StepStats> stepStats() {
        final Map<LoadBotState, StepStats> stats = new EnumMap<>(LoadBotState.class);
        for (LoadBotState state : LoadBotState.values()) {
            final Histogram histogram = stepHistogram(state);
            if (histogram.getTotalCount() == 0) {
                continue;
            }
            stats.put(
                    state,
                    StepStats.builder()
                            .count(histogram.getTotalCount())
                            .p50Millis(percentileMillis(histogram, 50.0))
                            .p99Millis(percentileMillis(histogram, 99.0))
                            .p999Millis(percentileMillis(histogram, 99.9))
                            .maxMillis(histogram.getMaxValue() / NANOS_PER_MILLI)
                            .build());
        }
        return Collections.unmodifiableMap(stats);
    }

    private static double percentileMillis(Histogram histogram, double percentile) {
        return histogram.getValueAtPercentile(percentile) / NANOS_PER_MILLI;
    }
}
