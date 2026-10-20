/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ManualGameClock implements GameClock {
    private final AtomicLong gameMillis = new AtomicLong(ORIGIN_MILLIS);
    private final AtomicLong wallMillis;
    private final Clock wallClock;

    public ManualGameClock(Instant wallStart, ZoneId zone) {
        this.wallMillis = new AtomicLong(wallStart.toEpochMilli());
        this.wallClock = new WallView(wallMillis, Objects.requireNonNull(zone, "zone"));
    }

    public void advance(Duration duration) {
        final long millis = requireNonNegative(duration);
        gameMillis.addAndGet(millis);
        wallMillis.addAndGet(millis);
    }

    public void advanceWall(Duration duration) {
        wallMillis.addAndGet(duration.toMillis());
    }

    @Override
    public long gameMillis() {
        return gameMillis.get();
    }

    @Override
    public Clock wallClock() {
        return wallClock;
    }

    private static long requireNonNegative(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Game time is monotonic, cannot advance by " + duration);
        }

        return duration.toMillis();
    }

    private static final class WallView extends Clock {
        private final AtomicLong millis;
        private final ZoneId zone;

        private WallView(AtomicLong millis, ZoneId zone) {
            this.millis = millis;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new WallView(millis, zone);
        }

        @Override
        public long millis() {
            return millis.get();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
