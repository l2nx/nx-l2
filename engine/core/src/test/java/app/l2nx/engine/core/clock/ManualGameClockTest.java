/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ManualGameClockTest {
    private static final Instant WALL_START = Instant.parse("2026-05-01T10:00:00Z");

    @Nested
    class GameMillis {
        @Test
        void gameMillis_shouldStartAtOrigin() {
            final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);

            assertThat(clock.gameMillis()).isEqualTo(GameClock.ORIGIN_MILLIS);
            assertThat(clock.wallMillis()).isEqualTo(WALL_START.toEpochMilli());
        }
    }

    @Nested
    class Advance {
        @Test
        void advance_shouldMoveGameAndWallTime() {
            final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);

            clock.advance(Duration.ofSeconds(90));

            assertThat(clock.gameMillis()).isEqualTo(GameClock.ORIGIN_MILLIS + 90_000);
            assertThat(clock.wallClock().instant()).isEqualTo(WALL_START.plusSeconds(90));
        }

        @Test
        void advance_shouldThrow_whenDurationIsNegative() {
            final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);

            assertThatIllegalArgumentException().isThrownBy(() -> clock.advance(Duration.ofMillis(-1)));
        }
    }

    @Nested
    class AdvanceWall {
        @Test
        void advanceWall_shouldLeaveGameTime() {
            final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);

            clock.advanceWall(Duration.ofHours(1));

            assertThat(clock.gameMillis()).isEqualTo(GameClock.ORIGIN_MILLIS);
            assertThat(clock.wallClock().instant()).isEqualTo(WALL_START.plusSeconds(3600));
        }
    }

    @Nested
    class WallClock {
        @Test
        void wallClock_shouldKeepReadingTheManualTime_whenZoneChanges() {
            final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);
            final Clock warsaw = clock.wallClock().withZone(ZoneId.of("Europe/Warsaw"));

            clock.advance(Duration.ofMinutes(5));

            assertThat(warsaw.instant()).isEqualTo(WALL_START.plusSeconds(300));
            assertThat(warsaw.getZone()).isEqualTo(ZoneId.of("Europe/Warsaw"));
        }
    }

    @Nested
    class Conversions {
        @Test
        void conversions_shouldRoundTrip() {
            final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);
            clock.advance(Duration.ofSeconds(10));
            final long deadline = clock.gameMillis() + 120_000;

            final long actual = clock.wallMillisAt(deadline);

            assertThat(actual).isEqualTo(WALL_START.toEpochMilli() + 130_000);
            assertThat(clock.gameMillisAtWall(actual)).isEqualTo(deadline);
        }

        @Test
        void gameMillisAtWall_shouldCountOfflineTime_whenProcessRestarted() {
            final ManualGameClock before = new ManualGameClock(WALL_START, ZoneOffset.UTC);
            final long wallEnd = before.wallMillisAt(before.gameMillis() + 300_000);

            final ManualGameClock after = new ManualGameClock(WALL_START.plusSeconds(120), ZoneOffset.UTC);
            final long actual = after.gameMillisAtWall(wallEnd);

            assertThat(actual - after.gameMillis()).isEqualTo(180_000);
        }
    }
}
