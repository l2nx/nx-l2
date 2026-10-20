/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import app.l2nx.engine.core.clock.GameClock;
import java.time.Clock;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import lombok.Builder;

/**
 * Until the world partition exists there is one process-wide clock and the generator is the calling thread's
 * {@code ThreadLocalRandom}, since legacy code still runs on many threads.
 *
 * <p>TODO: the bridge disappears once its last caller is handed the clock and generator of its partition or service.
 */
public final class ClockBridge {
    private static volatile Binding binding;

    private ClockBridge() {}

    public static void bind(GameClock clock, Supplier<? extends RandomGenerator> random) {
        binding = new Binding(clock, random);
    }

    public static GameClock clock() {
        return current().clock();
    }

    public static long gameMillis() {
        return current().clock().gameMillis();
    }

    public static long wallMillis() {
        return current().clock().wallMillis();
    }

    public static Clock wallClock() {
        return current().clock().wallClock();
    }

    /** Replaces {@code Calendar.getInstance()}: the wall-clock time in the wall clock's zone. */
    public static Calendar calendar() {
        final Clock wall = wallClock();
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(wall.getZone()));
        calendar.setTimeInMillis(wall.millis());
        return calendar;
    }

    public static RandomGenerator random() {
        return current().random().get();
    }

    /** Replaces the binding and returns the previous one, {@code null} when unbound; for tests. */
    static Binding swap(Binding next) {
        final Binding previous = binding;
        binding = next;
        return previous;
    }

    private static Binding current() {
        final Binding current = binding;
        if (current == null) {
            throw new IllegalStateException(
                    "The game clock is not bound: ClockBridge is used outside the Spring context.");
        }

        return current;
    }

    @Builder(toBuilder = true)
    record Binding(GameClock clock, Supplier<? extends RandomGenerator> random) {
        Binding {
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(random, "random");
        }
    }
}
