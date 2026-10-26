/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.taskmanagers;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        void getGameTicks_shouldFollowTheClock(ManualGameClock clock) {
            final GameTimeTaskManager manager = new GameTimeTaskManager();
            final int before = manager.getGameTicks();

            clock.advance(Duration.ofMillis(1_200));

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

    @Nested
    class IsNightAt {
        @ParameterizedTest(name = "{0} -> night {1}")
        @CsvSource({
            "00:00, true",
            "00:59:59.999, true",
            "01:00, false",
            "03:59:59, false",
            "04:00, true",
            "05:00, false",
            "20:30, true",
            "23:59:59, false"
        })
        void isNightAt_shouldBeTheFirstRealHourOfEveryFour(LocalTime localTime, boolean expected) {
            assertThat(GameTimeTaskManager.isNightAt(localTime)).isEqualTo(expected);
        }
    }

    @Nested
    class NextChangeAt {
        @ParameterizedTest(name = "{0} in {1} -> {2}")
        @CsvSource({
            "2026-01-05T00:30:00Z, UTC, 2026-01-05T01:00:00Z",
            "2026-01-05T01:00:00Z, UTC, 2026-01-05T04:00:00Z",
            "2026-01-05T22:00:00Z, UTC, 2026-01-06T00:00:00Z",
            "2026-03-29T00:30:00Z, Europe/Warsaw, 2026-03-29T02:00:00Z",
            "2026-10-24T23:30:00Z, Europe/Warsaw, 2026-10-25T03:00:00Z",
            "2026-10-25T00:30:00Z, Europe/Warsaw, 2026-10-25T03:00:00Z",
            "2026-10-25T01:30:00Z, Europe/Warsaw, 2026-10-25T03:00:00Z"
        })
        void nextChangeAt_shouldFollowTheLocalTimeOfDay(Instant now, ZoneId zone, Instant expected) {
            final Instant actual = GameTimeTaskManager.nextChangeAt(now.atZone(zone));

            assertThat(actual).isEqualTo(expected);
            assertThat(GameTimeTaskManager.isNightAt(
                            actual.minusMillis(1).atZone(zone).toLocalTime()))
                    .as("night or day flips at the change")
                    .isNotEqualTo(
                            GameTimeTaskManager.isNightAt(actual.atZone(zone).toLocalTime()));
        }
    }

    @Nested
    class Start {
        @Test
        void start_shouldTakeTheNightOfTheLocalTime_andFlipToDayOnce_whenDawnComes() {
            final World world = new World(Instant.parse("2026-01-05T03:59:59Z"));

            world.start();
            world.passAfter(Duration.ofSeconds(1));
            world.passAfter(Duration.ofSeconds(1));

            assertThat(world.manager().isNight()).isFalse();
            assertThat(world.manager().changes()).containsExactly(true, false);
        }

        @Test
        void start_shouldFlipToNight_atLocalFourInTheMorning_onDaylightSavingDay() {
            // 01:30 CET; the clocks jump from 02:00 to 03:00, so 04:00 CEST is an hour and a half later.
            final World world = new World(Instant.parse("2026-03-29T00:30:00Z"));

            world.start();
            world.passAfter(Duration.ofMinutes(89).plusSeconds(59));
            final List<Boolean> beforeChange = List.copyOf(world.manager().changes());
            world.passAfter(Duration.ofSeconds(1));

            assertThat(beforeChange).isEmpty();
            assertThat(world.manager().changes()).containsExactly(true);
        }

        @Test
        void start_shouldStillFlipAtTheNextChange_whenAListenerThrows() {
            // 04:59:59 CET, night; dawn comes in a second, the next night at 08:00 CET.
            final World world = new World(Instant.parse("2026-01-05T03:59:59Z"));

            world.start();
            world.manager().failNextChange();
            world.passAfter(Duration.ofSeconds(1));
            world.passAfter(Duration.ofHours(3));

            assertThat(world.manager().changes()).containsExactly(true, false, true);
            assertThat(world.manager().isNight()).isTrue();
        }
    }

    private static Clock utc(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static final class RecordingManager extends GameTimeTaskManager {
        private final List<Boolean> changes = new ArrayList<>();
        private boolean failNext;

        @Override
        void onChange(boolean night) {
            changes.add(night);
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("listener failed");
            }
        }

        void failNextChange() {
            failNext = true;
        }

        List<Boolean> changes() {
            return changes;
        }
    }

    /** A world loop of its own on a Warsaw clock: the shared world loop of the tests runs in UTC. */
    private record World(ManualGameClock clock, Loop loop, RecordingManager manager) {
        World(Instant wallStart) {
            this(new ManualGameClock(wallStart, WARSAW));
        }

        private World(ManualGameClock clock) {
            this(
                    clock,
                    new Loop(
                            PartitionRuntime.WORLD, clock, 42L, EngineProperties.defaults(), new SimpleMeterRegistry()),
                    new RecordingManager());
        }

        void start() {
            loop.execute(() -> manager.start(loop));
            loop.runPass(clock.gameMillis());
        }

        void passAfter(Duration step) {
            clock.advance(step);
            loop.runPass(clock.gameMillis());
        }
    }
}
