/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldLoopExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class DisconnectionTest {
    private final Player player = mock(Player.class);

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
