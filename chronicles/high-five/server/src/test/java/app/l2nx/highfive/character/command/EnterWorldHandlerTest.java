/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.PlayerWorldEntry;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class EnterWorldHandlerTest {
    private final EnterWorldHandler handler = new EnterWorldHandler();
    private final GameClient client = mock(GameClient.class);
    private final Player player = mock(Player.class);
    private static final Inet4Address ZERO = (Inet4Address) InetAddress.ofLiteral("0.0.0.0");
    private final EnterWorld command = new EnterWorld(List.of(ZERO));

    @Nested
    class Context {
        @Test
        void context_shouldBeASelectedCharacter() {
            assertThat(handler.context()).isSameAs(GameContexts.CHARACTER);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldEnterThePlayer() {
            when(player.getClient()).thenReturn(client);

            try (MockedStatic<PlayerWorldEntry> entry = mockStatic(PlayerWorldEntry.class)) {
                handler.handle(player, command);

                entry.verify(() -> PlayerWorldEntry.enter(player, command.route()));
            }
            verify(client, never()).closeNow();
        }

        @Test
        void handle_shouldCloseAndRethrow_whenEnteringFails() {
            when(player.getClient()).thenReturn(client);
            final IllegalStateException failure = new IllegalStateException("broken");

            try (MockedStatic<PlayerWorldEntry> entry = mockStatic(PlayerWorldEntry.class)) {
                entry.when(() -> PlayerWorldEntry.enter(player, command.route()))
                        .thenThrow(failure);

                assertThatThrownBy(() -> handler.handle(player, command)).isSameAs(failure);
            }

            verify(client).closeNow();
        }
    }
}
