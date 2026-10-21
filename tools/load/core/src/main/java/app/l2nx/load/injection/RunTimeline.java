/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * The time base of one run: the monotonic clock every load bot reads and the phase boundaries of the injection
 * profile on it, in nanoseconds of that clock. The run starts when the timeline is created.
 */
public final class RunTimeline {
    private final LongSupplier clock;
    private final int bots;
    private final long start;
    private final long rampNanos;
    private final long rampDownNanos;
    private final long steadyStart;
    private final long steadyEnd;
    private final long end;

    public RunTimeline(InjectionProfile profile, LongSupplier clock) {
        if (profile.bots() <= 0) {
            throw new IllegalArgumentException("A run needs at least one load bot: " + profile.bots());
        }
        this.clock = clock;
        bots = profile.bots();
        rampNanos = nonNegative("ramp", profile.ramp());
        final long warmUpNanos = nonNegative("warm-up", profile.warmUp());
        final long steadyNanos = nonNegative("steady", profile.steady());
        if (steadyNanos == 0) {
            throw new IllegalArgumentException("The steady window must not be empty");
        }
        rampDownNanos = nonNegative("ramp-down", profile.rampDown());
        start = clock.getAsLong();
        steadyStart = start + rampNanos + warmUpNanos;
        steadyEnd = steadyStart + steadyNanos;
        end = steadyEnd + rampDownNanos;
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

    public long steadyStart() {
        return steadyStart;
    }

    public long steadyEnd() {
        return steadyEnd;
    }

    /** When the last load bot has started to leave: the run is done from here on. */
    public long end() {
        return end;
    }

    /** When load bot {@code index} connects: the load bots start evenly over the ramp, the first at its start. */
    public long startAt(int index) {
        return start + share(rampNanos, index);
    }

    /** When load bot {@code index} leaves: evenly over the ramp-down, in the order the load bots started. */
    public long leaveAt(int index) {
        return steadyEnd + share(rampDownNanos, index);
    }

    private long share(long span, int index) {
        if (index < 0 || index >= bots) {
            throw new IndexOutOfBoundsException("Load bot " + index + " of " + bots);
        }
        return Math.multiplyExact(span, index) / bots;
    }

    /**
     * Whether {@code time} falls in the steady window, start inclusive and end exclusive. Times are compared by their
     * difference: nanoTime values may be negative and only differences are meaningful.
     */
    public boolean inSteady(long time) {
        return time - steadyStart >= 0 && time - steadyEnd < 0;
    }
}
