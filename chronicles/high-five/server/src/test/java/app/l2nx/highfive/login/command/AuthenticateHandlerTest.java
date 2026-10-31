/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.login.view.AuthenticationFailed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import app.l2nx.highfive.session.SessionKey;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class AuthenticateHandlerTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();
    private static final Authenticate ALICE =
            Authenticate.builder().login("alice").key(KEY).build();

    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService loginService = new LoginService(port, accounts, new ServerListing());
    private final AuthenticateHandler handler = new AuthenticateHandler(loginService);
    private final GameClient client = mock(GameClient.class);

    @Nested
    class Context {
        @Test
        void context_shouldBeTheLoginSoAnAuthenticatedClientIsRejected() {
            assertThat(handler.context()).isSameAs(GameContexts.LOGIN);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldHoldTheLoginAndAskTheLoginServer() {
            handler.handle(client, ALICE);

            verify(client).setAccountName("alice");
            final UUID requestId = accounts.pendingAuthentications().getFirst();
            assertThat(port.getCalls()).containsExactly(Call.of("authenticate", requestId, "alice", KEY));
            assertThat(loginService.findRequester(requestId)).isSameAs(client);
        }

        @Test
        void handle_shouldClose_whenAnotherSessionHoldsTheLogin() {
            accounts.reserve("alice", mock(GameClient.class));

            handler.handle(client, ALICE);

            verify(client).closeNow();
            verify(client, never()).setAccountName("alice");
            assertThat(port.getCalls()).isEmpty();
        }

        @Test
        void handle_shouldRefuseAndFreeTheLogin_whenTheLoginServerIsUnreachable() {
            final ClientPort clientPort = mock(ClientPort.class);
            when(client.port()).thenReturn(clientPort);
            port.setLinkUp(false);

            handler.handle(client, ALICE);

            verify(clientPort).emitAndClose(AuthenticationFailed.INSTANCE);
            assertThat(accounts.pendingAuthentications()).isEmpty();
            assertThat(accounts.reserve("alice", client)).isTrue();
        }
    }
}
