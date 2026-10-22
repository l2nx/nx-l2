/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.sevensigns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.entity.actor.instance.FestivalMonster;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.sevensigns.SevenSignsFestivalScoreRepository;
import app.l2nx.highfive.repository.sevensigns.SevenSignsStatusRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("FeatureConfig")
@ResourceLock("SevenSignsFestival")
class SevenSignsFestivalTest {
    private static final long MANAGER_START = 120_000;
    private static final long CYCLE = 2_280_000;
    private static final long LENGTH = 1_080_000;
    private static final long FIRST_SPAWN = 120_000;
    private static final long FIRST_SWARM = 300_000;
    private static final long SECOND_SPAWN = 540_000;
    private static final long SECOND_SWARM = 720_000;
    private static final long CHEST_SPAWN = 900_000;
    private static final long STEP = 1000;

    private final long[] savedConfig = new long[8];
    private MockedStatic<SevenSigns> sevenSigns;
    private MockedStatic<SpawnTable> spawnTables;
    private MockedConstruction<Spawn> spawns;
    private SevenSignsFestival festival;
    private long signupDelay;
    private long now;

    @BeforeEach
    void startFestival() {
        saveConfig();
        configure();
        final SevenSigns signs = mock(SevenSigns.class);
        when(signs.getMilliToPeriodChange()).thenReturn(Long.MAX_VALUE);
        sevenSigns = mockStatic(SevenSigns.class);
        sevenSigns.when(SevenSigns::getInstance).thenReturn(signs);
        spawnTables = mockStatic(SpawnTable.class);
        spawnTables.when(SpawnTable::getInstance).thenReturn(mock(SpawnTable.class));
        final FestivalMonster monster = mock(FestivalMonster.class, RETURNS_DEEP_STUBS);
        spawns = mockConstruction(
                Spawn.class, (spawn, context) -> when(spawn.doSpawn()).thenReturn(monster));
        signupDelay = signupDelay();
        festival = new SevenSignsFestival(
                mock(SevenSignsFestivalScoreRepository.class), mock(SevenSignsStatusRepository.class));
    }

    @AfterEach
    void restore() {
        if (festival.managerScheduledTask != null) {
            WorldLoopExtension.runInPass(() -> festival.managerScheduledTask.cancel(false));
        }
        // The loop outlives the test: a running festival finishes while its mocks still answer.
        for (int i = 0; i < 16; i++) {
            WorldLoopExtension.passAfter(Duration.ofMillis(CYCLE));
        }
        spawns.close();
        spawnTables.close();
        sevenSigns.close();
        restoreConfig();
    }

    @Nested
    class Cycle {
        @Test
        void cycle_shouldRunEveryStageOnItsTimer_whenParticipantsSignedUp() {
            final long closeSignup = start() + MANAGER_START + signupDelay;
            signUp();

            at(closeSignup - 1);
            assertThat(festival.isFestivalInitialized()).isFalse();
            at(closeSignup);
            assertThat(festival.isFestivalInitialized()).isTrue();
            final int witch = spawns.constructed().size();

            at(closeSignup + FIRST_SPAWN - 1);
            assertThat(festival.isFestivalInProgress()).isFalse();
            assertThat(spawns.constructed()).hasSize(witch);
            at(closeSignup + FIRST_SPAWN);
            assertThat(festival.isFestivalInProgress()).isTrue();
            final int primary = spawns.constructed().size();
            assertThat(primary).isGreaterThan(witch);

            at(closeSignup + SECOND_SPAWN - 1);
            assertThat(spawns.constructed()).hasSize(primary);
            at(closeSignup + SECOND_SPAWN);
            final int archers = spawns.constructed().size();
            assertThat(archers).isGreaterThan(primary);

            at(closeSignup + CHEST_SPAWN - 1);
            assertThat(spawns.constructed()).hasSize(archers);
            at(closeSignup + CHEST_SPAWN);
            assertThat(spawns.constructed().size()).isGreaterThan(archers);

            at(closeSignup + LENGTH - 1);
            assertThat(festival.isFestivalInProgress()).isTrue();
            at(closeSignup + LENGTH);
            assertThat(festival.isFestivalInProgress()).isFalse();
            assertThat(festival.isFestivalInitialized()).isFalse();
            assertThat(festival.duskFestivalParticipants).isEmpty();
        }

        @Test
        void cycle_shouldWaitAnotherSignup_whenNobodySignedUp() {
            final long closeSignup = start() + MANAGER_START + signupDelay;
            final long retry = closeSignup + Math.max(0, CYCLE - SevenSignsFestival.festivalSignupTime());

            at(closeSignup + 1);
            signUp();
            at(retry - 1);
            assertThat(festival.isFestivalInitialized()).isFalse();

            at(retry);
            assertThat(festival.isFestivalInitialized()).isTrue();
        }
    }

    @Nested
    class LateTick {
        @Test
        void run_shouldStartTheNextCycleWhenTheFestivalEnds_whenATickCameDuringIt() {
            final long end = festivalOverlappingTheNextTick();
            signUp();

            at(end + signupDelay - 1);
            assertThat(festival.isFestivalInitialized()).isFalse();
            at(end + signupDelay);
            assertThat(festival.isFestivalInitialized()).isTrue();
        }

        @Test
        void run_shouldDropTheLateTick_whenTheManagerWasCancelled() {
            final long end = festivalOverlappingTheNextTick(
                    () -> festival.getFestivalManagerSchedule().cancel(false));
            signUp();

            at(end + signupDelay);

            assertThat(festival.isFestivalInitialized()).isFalse();
        }

        private long festivalOverlappingTheNextTick() {
            return festivalOverlappingTheNextTick(() -> {});
        }

        // Nobody signs up in time, so the festival starts on the retry, which falls on the next tick: that tick
        // comes while the cycle runs. Returns when that festival ends.
        private long festivalOverlappingTheNextTick(Runnable whileRunning) {
            final long closeSignup = start() + MANAGER_START + signupDelay;
            final long retry = closeSignup + CYCLE - SevenSignsFestival.festivalSignupTime();
            final long end = retry + LENGTH;
            at(closeSignup + 1);
            signUp();
            at(retry);
            assertThat(festival.isFestivalInitialized()).isTrue();
            WorldLoopExtension.runInPass(whileRunning);
            at(end);
            assertThat(festival.isFestivalInitialized()).isFalse();
            return end;
        }
    }

    // A negative signup time runs the close at once.
    private static long signupDelay() {
        return Math.max(0, SevenSignsFestival.festivalSignupTime());
    }

    private long start() {
        final AtomicReference<Long> started = new AtomicReference<>();
        WorldLoopExtension.runInPass(() -> {
            festival.startFestivalManager();
            started.set(WorldPartition.loop().gameNow());
        });
        now = started.get();
        return now;
    }

    // A stage schedules the next from the time of its pass, so passes run every second, as a live loop runs them
    // well within its timer deadlines; a second pass at a time runs a stage scheduled without delay.
    private void at(long time) {
        while (now < time) {
            final long step = Math.min(STEP, time - now);
            WorldLoopExtension.passAfter(Duration.ofMillis(step));
            WorldLoopExtension.passAfter(Duration.ZERO);
            now += step;
        }
    }

    private void signUp() {
        WorldLoopExtension.runInPass(() -> festival.duskFestivalParticipants.put(0, new ArrayList<>()));
    }

    private static void configure() {
        FeatureConfig.ALT_FESTIVAL_MANAGER_START = MANAGER_START;
        FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH = CYCLE;
        FeatureConfig.ALT_FESTIVAL_LENGTH = LENGTH;
        FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN = FIRST_SPAWN;
        FeatureConfig.ALT_FESTIVAL_FIRST_SWARM = FIRST_SWARM;
        FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN = SECOND_SPAWN;
        FeatureConfig.ALT_FESTIVAL_SECOND_SWARM = SECOND_SWARM;
        FeatureConfig.ALT_FESTIVAL_CHEST_SPAWN = CHEST_SPAWN;
    }

    private void saveConfig() {
        savedConfig[0] = FeatureConfig.ALT_FESTIVAL_MANAGER_START;
        savedConfig[1] = FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH;
        savedConfig[2] = FeatureConfig.ALT_FESTIVAL_LENGTH;
        savedConfig[3] = FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN;
        savedConfig[4] = FeatureConfig.ALT_FESTIVAL_FIRST_SWARM;
        savedConfig[5] = FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN;
        savedConfig[6] = FeatureConfig.ALT_FESTIVAL_SECOND_SWARM;
        savedConfig[7] = FeatureConfig.ALT_FESTIVAL_CHEST_SPAWN;
    }

    private void restoreConfig() {
        FeatureConfig.ALT_FESTIVAL_MANAGER_START = savedConfig[0];
        FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH = savedConfig[1];
        FeatureConfig.ALT_FESTIVAL_LENGTH = savedConfig[2];
        FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN = savedConfig[3];
        FeatureConfig.ALT_FESTIVAL_FIRST_SWARM = savedConfig[4];
        FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN = savedConfig[5];
        FeatureConfig.ALT_FESTIVAL_SECOND_SWARM = savedConfig[6];
        FeatureConfig.ALT_FESTIVAL_CHEST_SPAWN = savedConfig[7];
    }
}
