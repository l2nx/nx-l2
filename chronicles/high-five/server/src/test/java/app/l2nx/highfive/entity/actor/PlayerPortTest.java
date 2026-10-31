/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.network.GameClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlayerPortTest {
    private final Player player = mock(Player.class, CALLS_REAL_METHODS);
    private final GameClient client = mock(GameClient.class);
    private final ClientPort port = mock(ClientPort.class);

    @Nested
    class GetPort {
        @Test
        void getPort_shouldBeTheClientPort() {
            when(client.port()).thenReturn(port);
            player.setClient(client);

            assertThat(player.getPort()).isSameAs(port);
        }

        @Test
        void getPort_shouldBeDetached_whenThePlayerHasNoClient() {
            player.setClient(null);

            assertThat(player.getPort()).isSameAs(ClientPort.DETACHED);
        }

        @Test
        void getPort_shouldBeDetached_whenTheClientIsDetached() {
            when(client.isDetached()).thenReturn(true);
            player.setClient(client);

            assertThat(player.getPort()).isSameAs(ClientPort.DETACHED);
        }
    }
}
