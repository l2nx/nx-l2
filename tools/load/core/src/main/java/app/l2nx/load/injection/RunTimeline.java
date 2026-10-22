/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Phase boundaries of one run in nanoseconds of the monotonic clock all load bots read; the run starts at creation.
 * The single rule of which samples the measured phase counts, so client, generator and server windows agree.
 */
public final class RunTimeline {
    private final LongSupplier clock;
    private final int bots;
    private final MeasuredPhase phase;
    private final CountDownLatch arriving;
    private final long start;
    private final long rampNanos;
    private final long rampDownNanos;
    private final long steadyEnd;
    private final long end;
    private final long measuredStart;
    private final long measuredEnd;

    /** @throws IllegalArgumentException when the profile is invalid or its measured phase is empty */
    public RunTimeline(InjectionProfile profile, MeasuredPhase phase, LongSupplier clock) {
        if (profile.bots() <= 0) {
            throw new IllegalArgumentException("A run needs at least one load bot: " + profile.bots());
        }
        this.clock = clock;
        bots = profile.bots();
        rampNanos = nonNegative("ramp", profile.ramp());
        final long warmUpNanos = nonNegative("warm-up", profile.warmUp());
        final long steadyNanos = nonNegative("steady", profile.steady());
        rampDownNanos = nonNegative("ramp-down", profile.rampDown());
        if (phase == null) {
            throw new IllegalArgumentException("The measured phase is required");
        }
        this.phase = phase;
        arriving = new CountDownLatch(bots);
        start = clock.getAsLong();
        final long steadyStart = start + rampNanos + warmUpNanos;
        steadyEnd = steadyStart + steadyNanos;
        end = steadyEnd + rampDownNanos;
        switch (phase) {
            case STEADY -> {
                if (steadyNanos == 0) {
                    throw new IllegalArgumentException("A run measuring the steady window needs a positive steady");
                }
                measuredStart = steadyStart;
                measuredEnd = steadyEnd;
            }
            case RAMP -> {
                if (rampNanos == 0) {
                    throw new IllegalArgumentException("A run measuring the ramp needs a positive ramp");
                }
                measuredStart = start;
                measuredEnd = start + rampNanos;
            }
            default -> throw new IllegalArgumentException("Unknown measured phase " + phase);
        }
    }

    private static long nonNegative(String name, Duration duration) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("The " + name + " must be zero or positive: " + duration);
        }
        return duration.toNanos();
    }

    public long now() {
        return clock.getAsLong();
    }

    public long start() {
        return start;
    }

    public long measuredStart() {
        return measuredStart;
    }

    /** Exclusive. In the ramp, samples run on past it until every load bot is done arriving. */
    public long measuredEnd() {
        return measuredEnd;
    }

    public long end() {
        return end;
    }

    public long startAt(int index) {
        return start + share(rampNanos, index);
    }

    public long leaveAt(int index) {
        return steadyEnd + share(rampDownNanos, index);
    }

    private long share(long span, int index) {
        if (index < 0 || index >= bots) {
            throw new IndexOutOfBoundsException("Load bot " + index + " of " + bots);
        }
        return Math.multiplyExact(span, index) / bots;
    }

    /** Compared by difference: nanoTime values may be negative and only differences are meaningful. */
    public boolean inMeasured(long time) {
        return time - measuredStart >= 0 && time - measuredEnd < 0;
    }

    /**
     * The steady window takes each sample by its own time; the ramp takes the whole arrival of a load bot whose
     * intended start lies in it, however late each sample completes.
     */
    public boolean countsArrival(int index, long time) {
        return inMeasured(phase == MeasuredPhase.RAMP ? startAt(index) : time);
    }

    public boolean countsAction(long time) {
        return phase == MeasuredPhase.STEADY && inMeasured(time);
    }

    /** In the ramp, counts while any load bot is still arriving, the same window as the server meters. */
    public boolean countsLag(long time) {
        return phase == MeasuredPhase.RAMP ? arriving.getCount() > 0 : inMeasured(time);
    }

    /** Called once per load bot, from any thread. */
    public void arrivalOver() {
        arriving.countDown();
    }

    /** The ramp is over once every load bot's arrival is over. */
    public boolean awaitMeasuredEnd(long time) throws InterruptedException {
        if (phase == MeasuredPhase.RAMP) {
            return arriving.await(Math.max(0, time - now()), TimeUnit.NANOSECONDS);
        }
        final boolean over = measuredEnd - time <= 0;
        final long delay = (over ? measuredEnd : time) - now();
        if (delay > 0) {
            TimeUnit.NANOSECONDS.sleep(delay);
        }
        return over;
    }
}
