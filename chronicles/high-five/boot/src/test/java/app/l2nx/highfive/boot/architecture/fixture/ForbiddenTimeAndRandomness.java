/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.fixture;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/** One class per forbidden use, so each must be reported on its own. */
public final class ForbiddenTimeAndRandomness {
    private ForbiddenTimeAndRandomness() {}

    public static final class CurrentTimeMillis {
        long read() {
            return System.currentTimeMillis();
        }
    }

    public static final class NanoTime {
        long read() {
            return System.nanoTime();
        }
    }

    public static final class TimeMethodReference {
        LongSupplier read() {
            return System::currentTimeMillis;
        }
    }

    public static final class InstantNow {
        Object read() {
            return Instant.now();
        }
    }

    public static final class LocalDateTimeNow {
        Object read() {
            return LocalDateTime.now();
        }
    }

    public static final class ZonedDateTimeNowInZone {
        Object read() {
            return ZonedDateTime.now(ZoneOffset.UTC);
        }
    }

    public static final class SystemClock {
        Object read() {
            return Clock.systemUTC();
        }
    }

    public static final class CalendarInstance {
        Object read() {
            return Calendar.getInstance();
        }
    }

    public static final class NoArgGregorianCalendar {
        Object read() {
            return new GregorianCalendar();
        }
    }

    public static final class NoArgDate {
        Object read() {
            return new Date();
        }
    }

    public static final class ThreadLocalRandomUse {
        int read() {
            return ThreadLocalRandom.current().nextInt(100);
        }
    }

    public static final class MathRandom {
        double read() {
            return Math.random();
        }
    }

    public static final class NewRandom {
        Object read() {
            return new Random(42L);
        }
    }

    public static final class DefaultRandomGenerator {
        Object read() {
            return RandomGenerator.getDefault();
        }
    }

    public static final class ShuffleWithoutGenerator {
        void shuffle(List<?> list) {
            Collections.shuffle(list);
        }
    }
}
