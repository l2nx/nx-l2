/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.taskmanagers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PlayerAutoSaveTaskManagerTest {
    private static final int HANDLE = 0x10000001;

    private final Player player = mock(Player.class);
    private final IdManager ids = mock(IdManager.class);
    private final UUID characterId = EntityIds.next();
    private MockedStatic<IdManager> idManager;

    @BeforeEach
    void setUp() {
        when(player.getObjectId()).thenReturn(HANDLE);
        when(player.isOnline()).thenReturn(true);
        when(ids.findId(HANDLE)).thenReturn(Optional.of(characterId));
        idManager = mockStatic(IdManager.class);
        idManager.when(IdManager::getInstance).thenReturn(ids);
    }

    @AfterEach
    void tearDown() {
        idManager.close();
    }

    @Nested
    class AutoSave {
        @Test
        void autoSave_shouldSaveAtOnce_whenNothingIsInFlight() {
            WorldLoopExtension.runInPass(() -> PlayerAutoSaveTaskManager.autoSave(player));

            verify(player).autoSave();
        }

        @Test
        void autoSave_shouldWaitForTheValueTransactionInFlight() {
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.inFlightCharacters().begin(characterId);
                PlayerAutoSaveTaskManager.autoSave(player);
            });
            verify(player, never()).autoSave();

            WorldLoopExtension.runInPass(
                    () -> WorldPartition.inFlightCharacters().end(characterId));

            verify(player).autoSave();
        }

        @Test
        void autoSave_shouldSkipThePlayer_whenItLeftWhileWaiting() {
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.inFlightCharacters().begin(characterId);
                PlayerAutoSaveTaskManager.autoSave(player);
            });
            when(player.isOnline()).thenReturn(false);

            WorldLoopExtension.runInPass(
                    () -> WorldPartition.inFlightCharacters().end(characterId));

            verify(player, never()).autoSave();
        }
    }
}
