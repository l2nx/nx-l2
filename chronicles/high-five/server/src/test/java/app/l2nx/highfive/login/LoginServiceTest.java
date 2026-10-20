/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.OnlineAccounts.PendingAuthentication;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.LoginFail;
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
        void authenticate_shouldAskTheLoginServer_andKeepTheClientWaiting() {
            service.authenticate("alice", client, KEY);

            assertThat(port.getCalls()).containsExactly(Call.of("authenticate", "alice", KEY));
            assertThat(accounts.takePending("alice"))
                    .isEqualTo(PendingAuthentication.builder()
                            .client(client)
                            .sessionKey(KEY)
                            .build());
        }

        @Test
        void authenticate_shouldCloseTheClient_andFreeItsLogin_whenTheLoginServerIsUnreachable() {
            port.setLinkUp(false);
            service.reserveLogin("alice", client);

            service.authenticate("alice", client, KEY);

            verify(client).close(any(LoginFail.class));
            verify(client).disconnect();
            assertThat(accounts.takePending("alice")).isNull();
            assertThat(service.reserveLogin("alice", client)).isTrue();
        }
    }

    @Nested
    class ChangePassword {
        private final UUID accountId = UUID.randomUUID();

        @Test
        void changePassword_shouldAwaitTheAnswer_whenTheRequestWentOut() {
            final boolean actual = service.changePassword(accountId, "Hero", "old", "new");

            assertThat(actual).isTrue();
            assertThat(port.getCalls()).containsExactly(Call.of("changePassword", accountId, "Hero", "old", "new"));
            assertThat(accounts.takeAllPasswordChanges()).containsExactly("Hero");
        }

        @Test
        void changePassword_shouldAwaitNothing_whenTheLoginServerIsUnreachable() {
            port.setLinkUp(false);

            final boolean actual = service.changePassword(accountId, "Hero", "old", "new");

            assertThat(actual).isFalse();
            assertThat(accounts.takeAllPasswordChanges()).isEmpty();
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
        void setAge_shouldStoreTheAge_andPublishItsCode() {
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
