/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.SessionAddress;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharSelectionInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LoginFail;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedConstruction;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class AuthenticationResultHandlerTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService loginService = new LoginService(port, accounts, new ServerListing());
    private final AuthenticationResultHandler handler = new AuthenticationResultHandler(port, loginService, accounts);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);

    AuthenticationResultHandlerTest() {
        when(session.attachment(GameClient.class)).thenReturn(client);
    }

    @Test
    void address_shouldBeTheRequest() {
        final UUID requestId = UUID.randomUUID();

        assertThat(AuthenticationResult.builder().requestId(requestId).build().address())
                .isEqualTo(new SessionAddress.Request(requestId));
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldEnterTheAccount_andShowTheCharacters_whenAccepted() {
            final UUID accountId = UUID.randomUUID();
            accounts.reserve("alice", client);
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            // CharSelectionInfo loads the account's characters from the database.
            try (MockedConstruction<CharSelectionInfo> selections = mockConstruction(CharSelectionInfo.class)) {
                handler.handle(
                        session,
                        AuthenticationResult.builder()
                                .requestId(requestId)
                                .accountId(accountId)
                                .accepted(true)
                                .build());

                final CharSelectionInfo selection = selections.constructed().getFirst();
                verify(client).sendPacket(selection);
                verify(client).setCharSelection(selection.getCharInfo());
            }

            verify(client).setAccountId(accountId);
            verify(client).authenticated();
            verify(client).setSessionId(KEY);
            assertThat(accounts.find(accountId)).isSameAs(client);
            assertThat(accounts.pendingAuthentications()).isEmpty();
            assertThat(port.getCalls()).containsExactly(Call.of("accountsEntered", List.of(accountId)));
        }

        @Test
        void handle_shouldCloseTheClient_andFreeItsLogin_whenRefused() {
            accounts.reserve("alice", client);
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(requestId)
                            .accepted(false)
                            .build());

            verify(client).close(any(LoginFail.class));
            verify(client).disconnect();
            assertThat(accounts.reserve("alice", client)).isTrue();
            assertThat(port.getCalls()).isEmpty();
        }

        @Test
        void handle_shouldIgnoreTheAnswer_whenTheRequestIsAnotherClients() {
            final GameClient other = mock(GameClient.class);
            final UUID requestId = accounts.awaitAuthentication("bob", other, KEY);

            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(requestId)
                            .accountId(UUID.randomUUID())
                            .accepted(true)
                            .build());

            verify(client, never()).setAccountId(any());
            assertThat(accounts.pendingAuthentications()).containsExactly(requestId);
            assertThat(port.getCalls()).isEmpty();
        }

        @Test
        void handle_shouldIgnoreTheAnswer_whenTheRequestIsGone() {
            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(UUID.randomUUID())
                            .accountId(UUID.randomUUID())
                            .accepted(true)
                            .build());

            verify(client, never()).setAccountId(any());
            assertThat(port.getCalls()).isEmpty();
        }
    }
}
