/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.taskmanagers;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(ClockBridgeExtension.class)
@ResourceLock(ClockBridgeExtension.LOCK)
class GameTimeTaskManagerTest {
    private static final Instant MIDNIGHT = Instant.parse("2026-01-05T00:00:00Z");
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Nested
    class TicksAt {
        @Test
        void ticksAt_shouldCountTenthsOfGameSeconds() {
            assertThat(GameTimeTaskManager.ticksAt(1_250)).isEqualTo(12);
            assertThat(GameTimeTaskManager.ticksAt(GameClock.ORIGIN_MILLIS))
                    .isEqualTo((int) (GameClock.ORIGIN_MILLIS / 100));
        }
    }

    @Nested
    class GetGameTicks {
        @Test
        void getGameTicks_shouldFollowTheClock_whenTheThreadIsNotStarted(ManualGameClock clock) {
            final GameTimeTaskManager manager = new GameTimeTaskManager();
            final int before = manager.getGameTicks();

            clock.advance(Duration.ofMillis(1_200));

            assertThat(manager.isAlive()).isFalse();
            assertThat(manager.getGameTicks()).isEqualTo(before + 12);
        }
    }

    @Nested
    class InGameMinutesAt {
        @Test
        void inGameMinutesAt_shouldBeMidnight_atRealMidnight() {
            assertThat(GameTimeTaskManager.inGameMinutesAt(utc(MIDNIGHT))).isZero();
        }

        @Test
        void inGameMinutesAt_shouldAdvanceSixTimesFaster() {
            assertThat(GameTimeTaskManager.inGameMinutesAt(utc(MIDNIGHT.plusSeconds(10 * 60))))
                    .isEqualTo(60);
            assertThat(GameTimeTaskManager.inGameMinutesAt(utc(MIDNIGHT.plusSeconds(4 * 3_600))))
                    .isZero();
        }

        @Test
        void inGameMinutesAt_shouldStayAnchoredToRealMidnight_afterDaylightSavingShift() {
            final Clock midnightAfterShift = Clock.fixed(Instant.parse("2026-03-29T22:00:00Z"), WARSAW);

            assertThat(GameTimeTaskManager.inGameMinutesAt(midnightAfterShift)).isZero();
        }

        @Test
        void inGameMinutesAt_shouldFollowTheLocalTimeOfDay_onDaylightSavingDay() {
            final Clock threeAmSummerTime = Clock.fixed(Instant.parse("2026-03-29T01:00:00Z"), WARSAW);

            assertThat(GameTimeTaskManager.inGameMinutesAt(threeAmSummerTime)).isEqualTo(18 * 60);
        }
    }

    private static Clock utc(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
