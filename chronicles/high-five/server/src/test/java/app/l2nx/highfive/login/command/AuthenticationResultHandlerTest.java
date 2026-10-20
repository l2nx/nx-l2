/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.ConnectionState;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.CharSelectionInfo;
import app.l2nx.highfive.network.serverpackets.LoginFail;
import java.util.List;
import java.util.UUID;
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

    @Test
    void accept_shouldEnterTheAccount_andShowTheCharacters_whenAccepted() {
        final UUID accountId = UUID.randomUUID();
        accounts.reserve("alice", client);
        accounts.awaitAuthentication("alice", client, KEY);

        // CharSelectionInfo loads the account's characters from the database.
        try (MockedConstruction<CharSelectionInfo> selections = mockConstruction(CharSelectionInfo.class)) {
            handler.accept(AuthenticationResult.builder()
                    .login("alice")
                    .accountId(accountId)
                    .accepted(true)
                    .build());

            final CharSelectionInfo selection = selections.constructed().getFirst();
            verify(client).sendPacket(selection);
            verify(client).setCharSelection(selection.getCharInfo());
        }

        verify(client).setAccountId(accountId);
        verify(client).setConnectionState(ConnectionState.AUTHENTICATED);
        verify(client).setSessionId(KEY);
        assertThat(accounts.find(accountId)).isSameAs(client);
        assertThat(port.getCalls()).containsExactly(Call.of("accountsEntered", List.of(accountId)));
    }

    @Test
    void accept_shouldCloseTheClient_andFreeItsLogin_whenRejected() {
        accounts.reserve("alice", client);
        accounts.awaitAuthentication("alice", client, KEY);

        handler.accept(
                AuthenticationResult.builder().login("alice").accepted(false).build());

        verify(client).close(any(LoginFail.class));
        verify(client).disconnect();
        assertThat(accounts.reserve("alice", client)).isTrue();
        assertThat(port.getCalls()).isEmpty();
    }

    @Test
    void accept_shouldIgnoreTheAnswer_whenNoClientWaits() {
        handler.accept(AuthenticationResult.builder()
                .login("bob")
                .accountId(UUID.randomUUID())
                .accepted(true)
                .build());

        assertThat(port.getCalls()).isEmpty();
    }
}
