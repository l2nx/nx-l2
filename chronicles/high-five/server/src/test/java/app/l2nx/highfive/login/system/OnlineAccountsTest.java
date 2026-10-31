/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.login.system.OnlineAccounts.PendingAuthentication;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.SessionKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OnlineAccountsTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final OnlineAccounts accounts = new OnlineAccounts();
    private final GameClient client = mock(GameClient.class);

    @Nested
    class PasswordChanges {
        @Test
        void takePasswordChange_shouldTakeTheRequestOnce() {
            final UUID requestId = accounts.awaitPasswordChange(client);

            assertThat(accounts.takePasswordChange(requestId, client)).isTrue();
            assertThat(accounts.takePasswordChange(requestId, client)).isFalse();
        }

        @Test
        void takePasswordChange_shouldKeepTheRequest_whenAnotherClientAsks() {
            final UUID requestId = accounts.awaitPasswordChange(client);

            assertThat(accounts.takePasswordChange(requestId, mock(GameClient.class)))
                    .isFalse();
            assertThat(accounts.pendingPasswordChanges()).containsExactly(requestId);
        }

        @Test
        void awaitPasswordChange_shouldGiveEachRequestItsOwnId() {
            assertThat(accounts.awaitPasswordChange(client)).isNotEqualTo(accounts.awaitPasswordChange(client));
        }
    }

    @Nested
    class Reserve {
        @Test
        void reserve_shouldRefuseASecondSession_whenTheLoginIsHeld() {
            accounts.reserve("alice", client);

            final boolean actual = accounts.reserve("alice", mock(GameClient.class));

            assertThat(actual).isFalse();
        }

        @Test
        void reserve_shouldAcceptTheLogin_whenItWasReleased() {
            accounts.reserve("alice", client);
            accounts.release("alice");

            final boolean actual = accounts.reserve("alice", client);

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class TakePending {
        @Test
        void takePending_shouldReturnTheWaitingEntryOnce() {
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            final PendingAuthentication actual = accounts.takePending(requestId, client);

            assertThat(actual)
                    .isEqualTo(PendingAuthentication.builder()
                            .login("alice")
                            .client(client)
                            .sessionKey(KEY)
                            .build());
            assertThat(accounts.takePending(requestId, client)).isNull();
        }

        @Test
        void takePending_shouldReturnNull_whenTheClientCancelled() {
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);
            accounts.cancelRequests(client);

            assertThat(accounts.takePending(requestId, client)).isNull();
            assertThat(accounts.requester(requestId)).isNull();
        }
    }

    @Nested
    class LeaveAll {
        @Test
        void leaveAll_shouldReturnEveryHeldAccountAndForgetThem() {
            final UUID first = UUID.randomUUID();
            final UUID second = UUID.randomUUID();
            accounts.enter(first, client);
            accounts.enter(second, mock(GameClient.class));

            final List<UUID> actual = accounts.leaveAll();

            assertThat(actual).containsExactlyInAnyOrder(first, second);
            assertThat(accounts.accountIds()).isEmpty();
        }
    }
}
