/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SystemGameClockTest {
    private static final Clock FIXED_WALL = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    @Nested
    class GameMillis {
        @Test
        void gameMillis_shouldStartAtOrigin() {
            final SystemGameClock clock = new SystemGameClock(FIXED_WALL);

            assertThat(clock.gameMillis())
                    .isGreaterThanOrEqualTo(GameClock.ORIGIN_MILLIS)
                    .isLessThan(GameClock.ORIGIN_MILLIS + 1_000);
        }

        @Test
        void gameMillis_shouldNeverDecrease() {
            final SystemGameClock clock = new SystemGameClock(FIXED_WALL);

            long previous = clock.gameMillis();
            for (int i = 0; i < 100_000; i++) {
                final long current = clock.gameMillis();
                assertThat(current).isGreaterThanOrEqualTo(previous);
                previous = current;
            }
        }
    }

    @Nested
    class WallClock {
        @Test
        void wallClock_shouldBeTheGivenClock() {
            final SystemGameClock clock = new SystemGameClock(FIXED_WALL);

            assertThat(clock.wallClock()).isSameAs(FIXED_WALL);
            assertThat(clock.wallMillis()).isEqualTo(FIXED_WALL.millis());
        }
    }

    @Nested
    class WallMillisAt {
        @Test
        void wallMillisAt_shouldShiftByTheGameTimeOffset() {
            final SystemGameClock clock = new SystemGameClock(FIXED_WALL);
            final long deadline = clock.gameMillis() + 60_000;

            final long actual = clock.wallMillisAt(deadline);

            assertThat(actual).isBetween(FIXED_WALL.millis() + 59_000, FIXED_WALL.millis() + 60_000);
        }
    }
}
