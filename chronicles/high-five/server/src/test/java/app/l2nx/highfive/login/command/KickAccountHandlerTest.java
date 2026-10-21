/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.SessionAddress;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class KickAccountHandlerTest {
    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService loginService = new LoginService(port, accounts, new ServerListing());
    private final KickAccountHandler handler = new KickAccountHandler(loginService);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);

    @Test
    void address_shouldBeTheAccount() {
        final UUID accountId = UUID.randomUUID();

        assertThat(new KickAccount(accountId).address()).isEqualTo(new SessionAddress.Account(accountId));
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldSaveAndRemoveThePlayer_withTheMessage_andFreeTheAccount() {
            final UUID accountId = UUID.randomUUID();
            final Disconnection disconnection = mock(Disconnection.class);
            when(session.attachment(GameClient.class)).thenReturn(client);
            when(client.getAccountName()).thenReturn("alice");
            accounts.reserve("alice", client);
            accounts.enter(accountId, client);

            try (MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                disconnections.when(() -> Disconnection.of(client)).thenReturn(disconnection);

                handler.handle(session, new KickAccount(accountId));
            }

            verify(disconnection).storeAndDeleteWith(any(SystemMessage.class));
            assertThat(accounts.find(accountId)).isNull();
            assertThat(accounts.reserve("alice", client)).isTrue();
            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }

        @Test
        void handle_shouldFreeTheAccount_whenRemovingThePlayerFails() {
            final UUID accountId = UUID.randomUUID();
            final Disconnection disconnection = mock(Disconnection.class);
            doThrow(new IllegalStateException("boom")).when(disconnection).storeAndDeleteWith(any());
            when(session.attachment(GameClient.class)).thenReturn(client);
            when(client.getAccountName()).thenReturn("alice");
            accounts.reserve("alice", client);
            accounts.enter(accountId, client);

            try (MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                disconnections.when(() -> Disconnection.of(client)).thenReturn(disconnection);

                assertThatThrownBy(() -> handler.handle(session, new KickAccount(accountId)))
                        .isInstanceOf(IllegalStateException.class);
            }

            assertThat(accounts.find(accountId)).isNull();
            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }
    }

    @Nested
    class HandleWithoutSession {
        @Test
        void handleWithoutSession_shouldFreeTheAccount_whenNoClientHoldsIt() {
            final UUID accountId = UUID.randomUUID();

            handler.handleWithoutSession(new KickAccount(accountId));

            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }

        @Test
        void handleWithoutSession_shouldSaveAndRemoveThePlayer_andFreeTheAccount_whenTheClientIsDetached() {
            final UUID accountId = holdDetached();
            final Disconnection disconnection = mock(Disconnection.class);

            try (MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                disconnections.when(() -> Disconnection.of(client)).thenReturn(disconnection);

                handler.handleWithoutSession(new KickAccount(accountId));
            }

            verify(disconnection).storeAndDeleteWith(any(SystemMessage.class));
            assertThat(accounts.find(accountId)).isNull();
            assertThat(accounts.reserve("alice", client)).isTrue();
            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }

        @Test
        void handleWithoutSession_shouldFreeTheAccount_whenRemovingThePlayerFails() {
            final UUID accountId = holdDetached();
            final Disconnection disconnection = mock(Disconnection.class);
            doThrow(new IllegalStateException("boom")).when(disconnection).storeAndDeleteWith(any());

            try (MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                disconnections.when(() -> Disconnection.of(client)).thenReturn(disconnection);

                assertThatThrownBy(() -> handler.handleWithoutSession(new KickAccount(accountId)))
                        .isInstanceOf(IllegalStateException.class);
            }

            assertThat(accounts.find(accountId)).isNull();
            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }

        @Test
        void handleWithoutSession_shouldTellTheLoginServerOnce_whenTheDetachedConnectionClosesAfterTheKick() {
            final UUID accountId = holdDetached();
            when(client.getAccountId()).thenReturn(accountId);

            try (MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                disconnections.when(() -> Disconnection.of(client)).thenReturn(mock(Disconnection.class));

                handler.handleWithoutSession(new KickAccount(accountId));
            }
            loginService.logout(client);

            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }

        private UUID holdDetached() {
            final UUID accountId = UUID.randomUUID();
            when(client.isDetached()).thenReturn(true);
            when(client.getAccountName()).thenReturn("alice");
            accounts.reserve("alice", client);
            accounts.enter(accountId, client);
            return accountId;
        }

        @Test
        void handleWithoutSession_shouldOnlyFreeTheAccount_whenTheClientIsNotDetached() {
            final UUID accountId = UUID.randomUUID();
            when(client.getAccountName()).thenReturn("alice");
            accounts.reserve("alice", client);
            accounts.enter(accountId, client);

            handler.handleWithoutSession(new KickAccount(accountId));

            verify(client, never()).close(any());
            verify(client).disconnect();
            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
        }
    }
}
