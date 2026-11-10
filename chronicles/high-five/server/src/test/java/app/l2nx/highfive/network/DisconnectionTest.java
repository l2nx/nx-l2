/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.clock.ClockBridgeExtension;
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

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class DisconnectionTest {
    private static final int HANDLE = 0x10000001;

    private final Player player = mock(Player.class);
    private final IdManager ids = mock(IdManager.class);
    private final UUID characterId = EntityIds.next();
    private MockedStatic<IdManager> idManager;

    @BeforeEach
    void bindIds() {
        when(player.getObjectId()).thenReturn(HANDLE);
        when(ids.findId(HANDLE)).thenReturn(Optional.of(characterId));
        idManager = mockStatic(IdManager.class);
        idManager.when(IdManager::getInstance).thenReturn(ids);
    }

    @AfterEach
    void releaseIds() {
        idManager.close();
    }

    @Nested
    class StoreAndDelete {
        @Test
        void storeAndDelete_shouldWaitForTheValueTransactionInFlight() {
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.inFlightCharacters().begin(characterId);
                Disconnection.of(player).storeAndDelete();
            });
            verify(player, never()).storeMe();

            WorldLoopExtension.runInPass(
                    () -> WorldPartition.inFlightCharacters().end(characterId));

            verify(player).storeMe();
        }

        @Test
        void storeAndDelete_shouldReleaseTheLoadedCharacter_afterTheSave() {
            final boolean released = WorldLoopExtension.callInPass(() -> {
                WorldPartition.loadedCharacters().loaded(characterId, player);
                Disconnection.of(player).storeAndDelete();
                return WorldPartition.loadedCharacters().find(characterId).isEmpty();
            });

            assertThat(released).isTrue();
            verify(player).storeMe();
        }

        @Test
        void storeAndDelete_shouldSaveAtOnce_whenTheCharacterWasNeverStored() {
            when(ids.findId(HANDLE)).thenReturn(Optional.empty());

            WorldLoopExtension.runInPass(() -> Disconnection.of(player).storeAndDelete());

            verify(player).storeMe();
        }
    }

    @Nested
    class OnDisconnection {
        @Test
        void onDisconnection_shouldSaveThePlayer_onTheWorldLoop() {
            when(player.canLogout()).thenReturn(true);

            WorldLoopExtension.runInPass(() -> Disconnection.of(player).onDisconnection());

            verify(player).storeMe();
        }

        @Test
        void onDisconnection_shouldNotSaveThePlayer_offTheWorldLoop() {
            when(player.canLogout()).thenReturn(true);

            Disconnection.of(player).onDisconnection();

            verify(player, never()).storeMe();
        }
    }
}
