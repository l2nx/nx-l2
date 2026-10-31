/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameContextsTest {
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);
    private final Player player = mock(Player.class);

    GameContextsTest() {
        when(session.attachment(GameClient.class)).thenReturn(client);
    }

    @Nested
    class LoginContext {
        @Test
        void resolve_shouldResolveTheClientUntilAnAccountIsAuthenticated() {
            assertThat(GameContexts.LOGIN.resolve(session)).isSameAs(client);

            when(client.getAccountName()).thenReturn("alice");

            assertThat(GameContexts.LOGIN.resolve(session)).isNull();
        }
    }

    @Nested
    class LobbyContext {
        @Test
        void resolve_shouldResolveTheClientWhileNoCharacterIsSelected() {
            when(client.getAccountName()).thenReturn("alice");

            assertThat(GameContexts.LOBBY.resolve(session)).isSameAs(client);

            when(client.getPlayer()).thenReturn(player);

            assertThat(GameContexts.LOBBY.resolve(session)).isNull();
        }
    }

    @Nested
    class CharacterContext {
        @Test
        void resolve_shouldResolveTheSelectedCharacterBeforeItEntersTheWorld() {
            when(client.getAccountName()).thenReturn("alice");
            when(client.getPlayer()).thenReturn(player);

            assertThat(GameContexts.CHARACTER.resolve(session)).isSameAs(player);
            assertThat(GameContexts.WORLD.resolve(session)).isNull();
        }
    }

    @Nested
    class WorldContext {
        @Test
        void resolve_shouldResolveTheCharacterOnceItEnteredTheWorld() {
            when(client.getAccountName()).thenReturn("alice");
            when(client.getPlayer()).thenReturn(player);
            when(player.hasEnteredWorld()).thenReturn(true);

            assertThat(GameContexts.WORLD.resolve(session)).isSameAs(player);
        }
    }

    @Nested
    class ContextName {
        @Test
        void name_shouldBeTheContextsNameSoCommandsAndLegacyPacketsShareTheRejectionTags() {
            assertThat(GameContexts.LOGIN.name()).isEqualTo("LOGIN");
            assertThat(GameContexts.LOBBY.name()).isEqualTo("LOBBY");
            assertThat(GameContexts.CHARACTER.name()).isEqualTo("CHARACTER");
            assertThat(GameContexts.WORLD.name()).isEqualTo("WORLD");
        }
    }
}
