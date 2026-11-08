/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.latency;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

/**
 * Lag of the generator itself: how late a load bot task ran against its intended time. A high lag means the event
 * loops, not the server, were saturated. Every event loop records concurrently; read after the run from one thread.
 */
public final class GeneratorLagMonitor {
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final Recorder recorder = new Recorder(3);
    private final Histogram total = new Histogram(3);
    private final Recorder[] levelRecorders;
    private final Histogram[] levelTotals;

    public GeneratorLagMonitor() {
        this(0);
    }

    /** @param levels the levels of a capacity run, each with a lag of its own; {@code 0} without levels */
    public GeneratorLagMonitor(int levels) {
        levelRecorders = new Recorder[levels];
        levelTotals = new Histogram[levels];
        for (int level = 0; level < levels; level++) {
            levelRecorders[level] = new Recorder(3);
            levelTotals[level] = new Histogram(3);
        }
    }

    /** Records one task; a negative lag (clock skew between the ticker and the run clock) counts as none. */
    public void record(long lagNanos) {
        recorder.recordValue(Math.max(0, lagNanos));
    }

    public void recordLevel(int level, long lagNanos) {
        levelRecorders[level].recordValue(Math.max(0, lagNanos));
    }

    /** All lag recorded so far, in nanoseconds. */
    public Histogram histogram() {
        total.add(recorder.getIntervalHistogram());
        return total.copy();
    }

    public long p99Nanos() {
        return histogram().getValueAtPercentile(99.0);
    }

    public double p99Millis() {
        return p99Nanos() / NANOS_PER_MILLI;
    }

    /** The lag of one level recorded so far, in nanoseconds; empty when no task of the level ran. */
    public Histogram levelHistogram(int level) {
        levelTotals[level].add(levelRecorders[level].getIntervalHistogram());
        return levelTotals[level].copy();
    }
}
