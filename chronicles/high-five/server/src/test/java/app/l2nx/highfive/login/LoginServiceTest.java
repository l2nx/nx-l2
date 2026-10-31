/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.login.view.AuthenticationFailed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.SessionKey;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

// LoginService binds a static bridge; setAge writes ServerConfig.
@ResourceLock("LoginService")
@ResourceLock("ServerConfig")
class LoginServiceTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService service = new LoginService(port, accounts, new ServerListing());
    private final GameClient client = mock(GameClient.class);

    @Nested
    class Authenticate {
        @Test
        void authenticate_shouldAskTheLoginServerAndKeepTheClientWaiting() {
            service.authenticate("alice", client, KEY);

            final UUID requestId = accounts.pendingAuthentications().getFirst();
            assertThat(port.getCalls()).containsExactly(Call.of("authenticate", requestId, "alice", KEY));
            assertThat(service.findRequester(requestId)).isSameAs(client);
        }

        @Test
        void authenticate_shouldCloseTheClientAndFreeItsLogin_whenTheLoginServerIsUnreachable() {
            final ClientPort clientPort = mock(ClientPort.class);
            when(client.port()).thenReturn(clientPort);
            port.setLinkUp(false);
            service.reserveLogin("alice", client);

            service.authenticate("alice", client, KEY);

            verify(clientPort).emitAndClose(AuthenticationFailed.INSTANCE);
            verify(client).disconnect();
            assertThat(accounts.pendingAuthentications()).isEmpty();
            assertThat(service.reserveLogin("alice", client)).isTrue();
        }
    }

    @Nested
    class ChangePassword {
        private final UUID accountId = UUID.randomUUID();

        @Test
        void changePassword_shouldAwaitTheAnswer_whenTheRequestWentOut() {
            when(client.getAccountId()).thenReturn(accountId);

            final boolean actual = service.changePassword(client, "Hero", "old", "new");

            final UUID requestId = accounts.pendingPasswordChanges().getFirst();
            assertThat(actual).isTrue();
            assertThat(port.getCalls())
                    .containsExactly(Call.of("changePassword", requestId, accountId, "Hero", "old", "new"));
        }

        @Test
        void changePassword_shouldAwaitNothing_whenTheLoginServerIsUnreachable() {
            when(client.getAccountId()).thenReturn(accountId);
            port.setLinkUp(false);

            final boolean actual = service.changePassword(client, "Hero", "old", "new");

            assertThat(actual).isFalse();
            assertThat(accounts.pendingPasswordChanges()).isEmpty();
        }
    }

    @Nested
    class Logout {
        @Test
        void logout_shouldFreeTheLoginSilently_whenTheLoginServerHasNotAcceptedIt() {
            service.reserveLogin("alice", client);

            service.logout("alice", null);

            verify(client).disconnect();
            assertThat(port.getCalls()).isEmpty();
            assertThat(service.reserveLogin("alice", client)).isTrue();
        }

        @Test
        void logout_shouldTellTheLoginServer_whenItAcceptedTheAccount() {
            final UUID accountId = UUID.randomUUID();
            accounts.enter(accountId, client);

            service.logout(null, accountId);

            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
            assertThat(service.findClient(accountId)).isNull();
        }
    }

    @Nested
    class LogoutClient {
        @Test
        void logout_shouldFreeTheClientsLoginAndAccountAndTellTheLoginServer() {
            final UUID accountId = holdAlice();

            service.logout(client);

            assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
            assertThat(service.findClient(accountId)).isNull();
            assertThat(service.reserveLogin("alice", mock(GameClient.class))).isTrue();
        }

        @Test
        void logout_shouldTellTheLoginServerNothing_whenAKickFreedTheAccountAlready() {
            final UUID accountId = holdAlice();
            service.logout("alice", accountId);
            port.getCalls().clear();

            service.logout(client);

            assertThat(port.getCalls()).isEmpty();
        }

        @Test
        void logout_shouldKeepTheLoginAndAccount_whenANewerSessionHoldsThem() {
            final UUID accountId = holdAlice();
            service.logout("alice", accountId);
            final GameClient newer = mock(GameClient.class);
            accounts.reserve("alice", newer);
            accounts.enter(accountId, newer);
            port.getCalls().clear();

            service.logout(client);

            assertThat(port.getCalls()).isEmpty();
            assertThat(service.findClient(accountId)).isSameAs(newer);
            assertThat(service.reserveLogin("alice", client)).isFalse();
        }

        private UUID holdAlice() {
            final UUID accountId = UUID.randomUUID();
            when(client.getAccountName()).thenReturn("alice");
            when(client.getAccountId()).thenReturn(accountId);
            accounts.reserve("alice", client);
            accounts.enter(accountId, client);
            return accountId;
        }
    }

    @Nested
    class LogoutAll {
        @Test
        void logoutAll_shouldTellTheLoginServerAboutEveryHeldAccount() {
            final UUID first = UUID.randomUUID();
            final UUID second = UUID.randomUUID();
            accounts.enter(first, client);
            accounts.enter(second, mock(GameClient.class));

            service.logoutAll();

            assertThat(port.getCalls())
                    .containsExactlyInAnyOrder(Call.of("accountLeft", first), Call.of("accountLeft", second));
            assertThat(accounts.accountIds()).isEmpty();
        }
    }

    @Nested
    class FindClient {
        @Test
        void findClient_shouldReturnNull_whenTheAccountIsNull() {
            assertThat(service.findClient(null)).isNull();
        }
    }

    @Nested
    class Listing {
        @Test
        void setStatus_shouldPublishTheStatusCode() {
            service.setStatus(ListingStatus.DOWN);

            assertThat(service.getStatus()).isEqualTo(ListingStatus.DOWN);
            assertThat(port.getCalls())
                    .containsExactly(Call.of("publishListing", Map.of(ListingAttribute.STATUS, 0x04)));
        }

        @Test
        void setAge_shouldStoreTheAgeAndPublishItsCode() {
            final int age = ServerConfig.SERVER_LIST_AGE;
            final int type = ServerConfig.SERVER_LIST_TYPE;
            try {
                service.setAge(12);

                assertThat(ServerConfig.SERVER_LIST_AGE).isEqualTo(12);
                assertThat(ServerConfig.SERVER_LIST_TYPE).as("the type stays").isEqualTo(type);
                assertThat(port.getCalls()).containsExactly(Call.of("publishListing", Map.of(ListingAttribute.AGE, 0)));
            } finally {
                ServerConfig.SERVER_LIST_AGE = age;
            }
        }
    }

    @Nested
    class GetInstance {
        @Test
        void getInstance_shouldReturnTheBoundService() {
            assertThat(LoginService.getInstance()).isSameAs(service);
        }
    }
}
