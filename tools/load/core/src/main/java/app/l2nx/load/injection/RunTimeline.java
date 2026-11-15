/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Phase boundaries of one run in nanoseconds of the monotonic clock all load bots read; the run starts at creation.
 * The single rule of which samples the measured phase counts, so client, generator and server windows agree. With
 * levels, the measured phase is the union of the level windows.
 */
public final class RunTimeline {
    private final LongSupplier clock;
    private final int bots;
    private final int firstLevel;
    private final int increment;
    private final MeasuredPhase phase;
    private final CountDownLatch arriving;
    private final long start;
    private final long rampNanos;
    private final long levelRampNanos;
    private final long rampDownNanos;
    private final long steadyEnd;
    private final long end;
    private final long measuredStart;
    private final long measuredEnd;
    private final long[] levelMeasuredStarts;
    private final long[] levelEnds;

    /** @throws IllegalArgumentException when the profile is invalid or its measured phase is empty */
    public RunTimeline(InjectionProfile profile, MeasuredPhase phase, LongSupplier clock) {
        check(profile, phase);
        this.clock = clock;
        this.phase = phase;
        firstLevel = profile.bots();
        bots = profile.totalBots();
        rampNanos = profile.ramp().toNanos();
        final long warmUpNanos = profile.warmUp().toNanos();
        final long steadyNanos = profile.steady().toNanos();
        rampDownNanos = profile.rampDown().toNanos();
        arriving = new CountDownLatch(bots);
        start = clock.getAsLong();
        final InjectionProfile.Levels levels = profile.levels();
        if (levels == null) {
            increment = 0;
            levelRampNanos = 0;
            levelMeasuredStarts = new long[0];
            levelEnds = new long[0];
            final long steadyStart = start + rampNanos + warmUpNanos;
            steadyEnd = steadyStart + steadyNanos;
            if (phase == MeasuredPhase.STEADY) {
                measuredStart = steadyStart;
                measuredEnd = steadyEnd;
            } else {
                measuredStart = start;
                measuredEnd = start + rampNanos;
            }
        } else {
            final long durationNanos = levels.duration().toNanos();
            increment = levels.increment();
            levelRampNanos = levels.ramp().toNanos();
            final int count = levels.count();
            levelMeasuredStarts = new long[count];
            levelEnds = new long[count];
            long levelStart = start + rampNanos;
            for (int level = 0; level < count; level++) {
                levelMeasuredStarts[level] = levelStart + warmUpNanos;
                levelEnds[level] = levelStart + durationNanos;
                levelStart = levelEnds[level] + levelRampNanos;
            }
            steadyEnd = levelEnds[count - 1];
            measuredStart = levelMeasuredStarts[0];
            measuredEnd = steadyEnd;
        }
        end = steadyEnd + rampDownNanos;
    }

    /**
     * The constructor's checks, so a run can refuse a profile before it starts the clock by building the timeline.
     *
     * @throws IllegalArgumentException when the profile is invalid or its measured phase is empty
     */
    public static void check(InjectionProfile profile, MeasuredPhase phase) {
        if (profile.bots() <= 0) {
            throw new IllegalArgumentException("A run needs at least one load bot: " + profile.bots());
        }
        final long rampNanos = nonNegative("ramp", profile.ramp());
        final long warmUpNanos = nonNegative("warm-up", profile.warmUp());
        final long steadyNanos = nonNegative("steady", profile.steady());
        nonNegative("ramp-down", profile.rampDown());
        if (phase == null) {
            throw new IllegalArgumentException("The measured phase is required");
        }
        final InjectionProfile.Levels levels = profile.levels();
        if (levels != null) {
            if (phase != MeasuredPhase.STEADY) {
                throw new IllegalArgumentException(
                        "A run with levels measures each level like a steady window, not the " + phase);
            }
            if (levels.duration().toNanos() <= warmUpNanos) {
                throw new IllegalArgumentException("A level of " + levels.duration()
                        + " leaves nothing to measure after the warm-up of " + profile.warmUp());
            }
            return;
        }
        switch (phase) {
            case STEADY -> {
                if (steadyNanos == 0) {
                    throw new IllegalArgumentException("A run measuring the steady window needs a positive steady");
                }
            }
            case RAMP -> {
                if (rampNanos == 0) {
                    throw new IllegalArgumentException("A run measuring the ramp needs a positive ramp");
                }
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
        checkIndex(index);
        if (index < firstLevel) {
            return start + share(rampNanos, index, firstLevel);
        }
        final int added = index - firstLevel;
        return levelEnds[added / increment] + share(levelRampNanos, added % increment, increment);
    }

    public long leaveAt(int index) {
        checkIndex(index);
        return steadyEnd + share(rampDownNanos, index, bots);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= bots) {
            throw new IndexOutOfBoundsException("Load bot " + index + " of " + bots);
        }
    }

    private static long share(long span, int index, int of) {
        return Math.multiplyExact(span, index) / of;
    }

    /** Compared by difference: nanoTime values may be negative and only differences are meaningful. */
    public boolean inMeasured(long time) {
        if (levelEnds.length > 0) {
            return levelAt(time) >= 0;
        }
        return time - measuredStart >= 0 && time - measuredEnd < 0;
    }

    /** The level whose measured window holds {@code time}; {@code -1} outside every one and in a run without levels. */
    public int levelAt(long time) {
        for (int level = 0; level < levelEnds.length; level++) {
            if (time - levelMeasuredStarts[level] >= 0 && time - levelEnds[level] < 0) {
                return level;
            }
        }
        return -1;
    }

    /** {@code 0} in a run without levels. */
    public int levels() {
        return levelEnds.length;
    }

    public int levelBots(int level) {
        Objects.checkIndex(level, levelEnds.length);
        return firstLevel + level * increment;
    }

    public long levelMeasuredStart(int level) {
        return levelMeasuredStarts[level];
    }

    public long levelEnd(int level) {
        return levelEnds[level];
    }

    /**
     * The whole arrival of a load bot belongs to the phase of its intended start, however late each sample completes:
     * a ramp arrival stalled into the steady window is not an action of a load bot already in the game.
     */
    public boolean countsArrival(int index) {
        return inMeasured(startAt(index));
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
