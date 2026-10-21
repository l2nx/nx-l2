/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.network.GameClient;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LinkLostHandlerTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final OnlineAccounts accounts = new OnlineAccounts();
    private final CommandDispatcher commands = mock(CommandDispatcher.class);
    private final LinkLostHandler handler = new LinkLostHandler(accounts, new ObjectProvider<>() {
        @Override
        public CommandDispatcher getObject() {
            return commands;
        }
    });

    @Nested
    class Handle {
        @Test
        void handle_shouldRefuseEveryPendingAuthentication_byItsRequest() {
            final UUID alice = accounts.awaitAuthentication("alice", mock(GameClient.class), KEY);
            final UUID bob = accounts.awaitAuthentication("bob", mock(GameClient.class), KEY);

            handler.handle(new LinkLost());

            verify(commands)
                    .submit(AuthenticationResult.builder()
                            .requestId(alice)
                            .accepted(false)
                            .build());
            verify(commands)
                    .submit(AuthenticationResult.builder()
                            .requestId(bob)
                            .accepted(false)
                            .build());
        }

        @Test
        void handle_shouldFailEveryPendingPasswordChange_byItsRequest() {
            final UUID requestId = accounts.awaitPasswordChange(mock(GameClient.class));

            handler.handle(new LinkLost());

            verify(commands)
                    .submit(PasswordChangeResult.builder()
                            .requestId(requestId)
                            .message(LoginService.PASSWORD_CHANGE_FAILED)
                            .build());
        }

        @Test
        void handle_shouldKeepTheAuthenticatedSessions() {
            final GameClient player = mock(GameClient.class);
            final UUID accountId = UUID.randomUUID();
            accounts.enter(accountId, player);

            handler.handle(new LinkLost());

            verify(commands, never()).submit(any());
            assertThat(accounts.find(accountId)).isSameAs(player);
        }
    }
}
