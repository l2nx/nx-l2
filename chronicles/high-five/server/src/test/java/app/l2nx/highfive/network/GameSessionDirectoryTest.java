/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.SessionAddress;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class GameSessionDirectoryTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final OnlineAccounts accounts = new OnlineAccounts();
    private final GameSessionDirectory directory =
            new GameSessionDirectory(new LoginService(new FakeLoginPort(), accounts, new ServerListing()));
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);

    GameSessionDirectoryTest() {
        when(client.session()).thenReturn(session);
    }

    @Nested
    class FindAccount {
        @Test
        void find_shouldReturnTheSessionOfTheAccount() {
            final UUID accountId = UUID.randomUUID();
            accounts.enter(accountId, client);

            assertThat(directory.find(new SessionAddress.Account(accountId))).containsSame(session);
        }

        @Test
        void find_shouldReturnNothing_whenNoClientHoldsTheAccount() {
            assertThat(directory.find(new SessionAddress.Account(UUID.randomUUID())))
                    .isEmpty();
        }

        @Test
        void find_shouldReturnNothing_whenTheClientIsDetached() {
            final UUID accountId = UUID.randomUUID();
            when(client.isDetached()).thenReturn(true);
            accounts.enter(accountId, client);

            assertThat(directory.find(new SessionAddress.Account(accountId))).isEmpty();
        }
    }

    @Nested
    class FindRequest {
        @Test
        void find_shouldReturnTheSessionWaitingForAnAuthentication() {
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            assertThat(directory.find(new SessionAddress.Request(requestId))).containsSame(session);
        }

        @Test
        void find_shouldReturnTheSessionWaitingForAPasswordChange() {
            final UUID requestId = accounts.awaitPasswordChange(client);

            assertThat(directory.find(new SessionAddress.Request(requestId))).containsSame(session);
        }

        @Test
        void find_shouldReturnNothing_afterTheSessionClosed() {
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            accounts.cancelRequests(client);

            assertThat(directory.find(new SessionAddress.Request(requestId))).isEmpty();
        }
    }
}
