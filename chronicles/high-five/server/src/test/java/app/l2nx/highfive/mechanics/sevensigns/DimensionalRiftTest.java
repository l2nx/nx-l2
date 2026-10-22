/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.sevensigns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.managers.DimensionalRiftManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("GeneralConfig")
class DimensionalRiftTest {
    private static final byte TYPE = 1;
    private static final byte ROOM = 1;
    private static final int JUMP_MIN_SECONDS = 480;
    private static final int JUMP_MAX_SECONDS = 600;

    private final Party party = mock(Party.class);
    private final DimensionalRiftRoom room =
            new DimensionalRiftRoom(TYPE, ROOM, -1000, 1000, -1000, 1000, -100, 100, 0, 0, 0, false);

    private MockedStatic<DimensionalRiftManager> managers;
    private int jumpMin;
    private int jumpMax;
    private int maxJumps;
    private int spawnDelay;

    @BeforeEach
    void stubRooms() {
        jumpMin = GeneralConfig.RIFT_AUTO_JUMPS_TIME_MIN;
        jumpMax = GeneralConfig.RIFT_AUTO_JUMPS_TIME_MAX;
        maxJumps = GeneralConfig.RIFT_MAX_JUMPS;
        spawnDelay = GeneralConfig.RIFT_SPAWN_DELAY;
        GeneralConfig.RIFT_AUTO_JUMPS_TIME_MIN = JUMP_MIN_SECONDS;
        GeneralConfig.RIFT_AUTO_JUMPS_TIME_MAX = JUMP_MAX_SECONDS;
        GeneralConfig.RIFT_MAX_JUMPS = 4;
        GeneralConfig.RIFT_SPAWN_DELAY = 10_000;

        final DimensionalRiftManager manager = mock(DimensionalRiftManager.class);
        when(manager.getRoom(TYPE, ROOM)).thenReturn(room);
        managers = mockStatic(DimensionalRiftManager.class);
        managers.when(DimensionalRiftManager::getInstance).thenReturn(manager);
    }

    @AfterEach
    void restore() {
        managers.close();
        GeneralConfig.RIFT_AUTO_JUMPS_TIME_MIN = jumpMin;
        GeneralConfig.RIFT_AUTO_JUMPS_TIME_MAX = jumpMax;
        GeneralConfig.RIFT_MAX_JUMPS = maxJumps;
        GeneralConfig.RIFT_SPAWN_DELAY = spawnDelay;
    }

    @Nested
    class KillRift {
        @Test
        void killRift_shouldNotThrow_whenRiftIsBetweenRooms() {
            final DimensionalRift rift = enter();
            rift.currentRoom = -1;
            final AtomicReference<Throwable> thrown = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> thrown.set(catchThrowable(rift::killRift)));

            assertThat(thrown.get()).isNull();
            verify(party).setDimensionalRift(null);
        }

        @Test
        void killRift_shouldCancelTheJump_whenRiftIsBetweenRooms() {
            final DimensionalRift rift = enter();
            rift.currentRoom = -1;
            WorldLoopExtension.runInPass(rift::killRift);

            WorldLoopExtension.passAfter(Duration.ofSeconds(JUMP_MAX_SECONDS));

            verify(party, times(1)).setDimensionalRift(null);
        }

        @Test
        void killRift_shouldEmptyTheRoom_whenRiftIsInARoom() {
            final DimensionalRift rift = enter();
            assertThat(room.isPartyInside()).isTrue();

            WorldLoopExtension.runInPass(rift::killRift);

            assertThat(room.isPartyInside()).isFalse();
        }
    }

    @Nested
    class CreateTeleporterTimer {
        @Test
        void createTeleporterTimer_shouldJumpInAWorldPass_whenTheJumpTimeHasPassed() {
            final List<Boolean> endedInLoop = new ArrayList<>();
            doAnswer(invocation -> {
                        if (invocation.getArgument(0) == null) {
                            endedInLoop.add(WorldPartition.loop().inLoop());
                        }
                        return null;
                    })
                    .when(party)
                    .setDimensionalRift(any());
            enter();

            WorldLoopExtension.passAfter(Duration.ofSeconds(JUMP_MIN_SECONDS - 1));
            assertThat(endedInLoop).isEmpty();

            WorldLoopExtension.passAfter(Duration.ofSeconds(JUMP_MAX_SECONDS - JUMP_MIN_SECONDS + 1));
            assertThat(endedInLoop).containsExactly(true);
            assertThat(room.isPartyInside()).isFalse();
        }
    }

    private DimensionalRift enter() {
        final AtomicReference<DimensionalRift> rift = new AtomicReference<>();
        WorldLoopExtension.runInPass(() -> rift.set(new DimensionalRift(party, TYPE, ROOM)));
        return rift.get();
    }
}
