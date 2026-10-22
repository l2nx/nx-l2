/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.loop.Loop;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import lombok.Builder;

/**
 * Answers by the calling thread: on a loop thread with the pass time and that loop's generator, so a pass sees one
 * "now" and replays; elsewhere only wall-clock time, as game time or randomness there mean a second writer.
 *
 * <p>TODO: the bridge disappears once its last caller is handed the clock and generator of its partition or service.
 */
public final class ClockBridge {
    private static volatile TimeSource source;

    private ClockBridge() {}

    public static void bindLoops(GameClock clock) {
        source = new LoopTimeSource(clock);
    }

    static void bind(GameClock clock, Supplier<? extends RandomGenerator> random) {
        source = new FixedTimeSource(clock, random);
    }

    public static GameClock clock() {
        return current().clock();
    }

    public static long gameMillis() {
        return current().gameMillis();
    }

    public static long wallMillis() {
        return current().wallMillis();
    }

    public static Clock wallClock() {
        return current().wallClock();
    }

    /** Replaces {@code Calendar.getInstance()}: the wall-clock time in the wall clock's zone. */
    public static Calendar calendar() {
        final Clock wall = wallClock();
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(wall.getZone()));
        calendar.setTimeInMillis(wall.millis());
        return calendar;
    }

    public static RandomGenerator random() {
        return current().random();
    }

    /** Replaces the binding and returns the previous one, {@code null} when unbound; for tests. */
    static TimeSource swap(TimeSource next) {
        final TimeSource previous = source;
        source = next;
        return previous;
    }

    private static TimeSource current() {
        final TimeSource current = source;
        if (current == null) {
            throw new IllegalStateException(
                    "The game clock is not bound: ClockBridge is used outside the Spring context.");
        }

        return current;
    }

    sealed interface TimeSource permits LoopTimeSource, FixedTimeSource {
        GameClock clock();

        long gameMillis();

        long wallMillis();

        Clock wallClock();

        RandomGenerator random();
    }

    static final class LoopTimeSource implements TimeSource {
        private final GameClock clock;
        private final PassClock passClock;
        private final PassGameClock passGameClock;

        LoopTimeSource(GameClock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            this.passClock = new PassClock(clock.wallClock());
            this.passGameClock = new PassGameClock(passClock);
        }

        @Override
        public GameClock clock() {
            loop("The game clock");
            return passGameClock;
        }

        @Override
        public long gameMillis() {
            return passGameClock.gameMillis();
        }

        @Override
        public long wallMillis() {
            final Loop loop = Loop.current();
            return loop != null ? loop.wallNow() : clock.wallMillis();
        }

        @Override
        public Clock wallClock() {
            return Loop.current() != null ? passClock : clock.wallClock();
        }

        @Override
        public RandomGenerator random() {
            return loop("The random generator").random();
        }

        static Loop loop(String what) {
            final Loop loop = Loop.current();
            if (loop == null) {
                throw new IllegalStateException(what + " is read off a loop thread, on "
                        + Thread.currentThread().getName() + ": a second writer");
            }
            return loop;
        }
    }

    static final class PassGameClock implements GameClock {
        private final PassClock passClock;

        PassGameClock(PassClock passClock) {
            this.passClock = passClock;
        }

        @Override
        public long gameMillis() {
            return LoopTimeSource.loop("Game time").gameNow();
        }

        @Override
        public Clock wallClock() {
            return passClock;
        }

        @Override
        public long wallMillis() {
            return passClock.millis();
        }
    }

    static final class PassClock extends Clock {
        private final Clock wall;

        PassClock(Clock wall) {
            this.wall = Objects.requireNonNull(wall, "wall");
        }

        @Override
        public ZoneId getZone() {
            return wall.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(wall.getZone()) ? this : new PassClock(wall.withZone(zone));
        }

        @Override
        public long millis() {
            final Loop loop = Loop.current();
            return loop != null ? loop.wallNow() : wall.millis();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }
    }

    @Builder(toBuilder = true)
    record FixedTimeSource(GameClock clock, Supplier<? extends RandomGenerator> generators) implements TimeSource {
        FixedTimeSource {
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(generators, "generators");
        }

        @Override
        public long gameMillis() {
            return clock.gameMillis();
        }

        @Override
        public long wallMillis() {
            return clock.wallMillis();
        }

        @Override
        public Clock wallClock() {
            return clock.wallClock();
        }

        @Override
        public RandomGenerator random() {
            return generators.get();
        }
    }
}
