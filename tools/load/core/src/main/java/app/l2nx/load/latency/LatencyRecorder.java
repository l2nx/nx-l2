/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.latency;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.report.model.IntentStats;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

/**
 * Client-side results per intent kind: latency from the intended time in nanoseconds, and the counts of intents sent,
 * acknowledged, timed out and lost to a load bot error. Load bots record only intents intended in the steady window.
 * Every event loop records concurrently and wait-free; read after the run from one thread.
 */
public final class LatencyRecorder {
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final Map<IntentKind, Recorder> latencies = new EnumMap<>(IntentKind.class);
    private final Map<IntentKind, Histogram> totals = new EnumMap<>(IntentKind.class);
    private final Map<IntentKind, LongAdder> sent = counters();
    private final Map<IntentKind, LongAdder> timeouts = counters();
    private final Map<IntentKind, LongAdder> errors = counters();

    public LatencyRecorder() {
        for (IntentKind kind : IntentKind.values()) {
            latencies.put(kind, new Recorder(3));
            totals.put(kind, new Histogram(3));
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

    /** The server rejected the intent, or it was lost because its load bot stopped on an error. */
    public void recordError(IntentKind kind) {
        errors.get(kind).increment();
    }

    /** Latencies of {@code kind} recorded so far, in nanoseconds. */
    public Histogram histogram(IntentKind kind) {
        final Histogram total = totals.get(kind);
        total.add(latencies.get(kind).getIntervalHistogram());
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

    /** Results of every kind that sent at least one intent, in milliseconds. */
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
                            .p50Millis(histogram.getValueAtPercentile(50.0) / NANOS_PER_MILLI)
                            .p99Millis(histogram.getValueAtPercentile(99.0) / NANOS_PER_MILLI)
                            .p999Millis(histogram.getValueAtPercentile(99.9) / NANOS_PER_MILLI)
                            .maxMillis(histogram.getMaxValue() / NANOS_PER_MILLI)
                            .build());
        }
        return Collections.unmodifiableMap(stats);
    }
}
