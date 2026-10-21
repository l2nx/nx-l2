/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.network.GameClient;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordChangeResultHandlerTest {
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final PasswordChangeResultHandler handler = new PasswordChangeResultHandler(accounts);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);
    private final Player player = mock(Player.class);

    PasswordChangeResultHandlerTest() {
        when(session.attachment(GameClient.class)).thenReturn(client);
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldTellThePlayer_andEndTheWait() {
            when(client.getPlayer()).thenReturn(player);
            final UUID requestId = accounts.awaitPasswordChange(client);

            handler.handle(session, result(requestId));

            verify(player).sendMessage("Password changed.");
            assertThat(accounts.pendingPasswordChanges()).isEmpty();
        }

        @Test
        void handle_shouldEndTheWait_whenTheSessionHasNoCharacterInGame() {
            final UUID requestId = accounts.awaitPasswordChange(client);

            handler.handle(session, result(requestId));

            assertThat(accounts.pendingPasswordChanges()).isEmpty();
        }

        @Test
        void handle_shouldTellNobody_whenTheRequestIsGone() {
            when(client.getPlayer()).thenReturn(player);

            handler.handle(session, result(UUID.randomUUID()));

            verify(player, never()).sendMessage(anyString());
        }
    }

    private static PasswordChangeResult result(UUID requestId) {
        return PasswordChangeResult.builder()
                .requestId(requestId)
                .message("Password changed.")
                .build();
    }
}
