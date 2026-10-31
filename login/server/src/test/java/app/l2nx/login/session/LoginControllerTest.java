/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.account.AccountInfo;
import app.l2nx.login.account.AccountRepository;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.GameServerRepository;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.LoginKeys;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.session.model.LoginResult;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;

class LoginControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("0190a6b2-0000-7000-8000-000000000001");
    private static final Duration LOGIN_TIMEOUT = LoginController.LOGIN_TIMEOUT;

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> purgeTask = mock(ScheduledFuture.class);
    private final LoginProperties properties = TestLoginProperties.create(new File("."));
    private final GameServerTable gameServers = new GameServerTable(mock(GameServerRepository.class), properties);
    private final LoginController controller =
            new LoginController(mock(AccountRepository.class), gameServers, taskScheduler);

    @BeforeEach
    void setUp() {
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> purgeTask);
    }

    @Nested
    class Load {
        @Test
        void load_shouldSchedulePurgeEveryLoginTimeout() throws Exception {
            final Instant before = Instant.now();
            controller.load();
            final Instant after = Instant.now();

            final ArgumentCaptor<Instant> startTime = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), startTime.capture(), eq(LOGIN_TIMEOUT));
            assertThat(startTime.getValue()).isBetween(before.plus(LOGIN_TIMEOUT), after.plus(LOGIN_TIMEOUT));
        }
    }

    @Nested
    class Purge {
        @Test
        void purge_shouldCloseClient_whenLoginTimedOut() throws Exception {
            final LoginClient client = client(false, System.currentTimeMillis() - LOGIN_TIMEOUT.toMillis(), true);

            runPurge();

            verify(client).closeForExpiry();
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        @Test
        void purge_shouldCloseClient_whenDisconnected() throws Exception {
            final LoginClient client = client(true, System.currentTimeMillis(), false);

            runPurge();

            verify(client).closeForExpiry();
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        @Test
        void purge_shouldKeepClient_whenJoinedGameServer() throws Exception {
            final LoginClient client = client(true, System.currentTimeMillis() - LOGIN_TIMEOUT.toMillis(), true);

            runPurge();

            verify(client, never()).closeForExpiry();
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isSameAs(client);
        }

        private LoginClient client(boolean joinedGameServer, long connectionStartTime, boolean connected) {
            final LoginClient client = mock(LoginClient.class);
            when(client.hasJoinedGS()).thenReturn(joinedGameServer);
            when(client.getConnectionStartTime()).thenReturn(connectionStartTime);
            when(client.isConnected()).thenReturn(connected);
            controller.loginServerClients.put(ACCOUNT_ID, client);
            return client;
        }

        private void runPurge() throws Exception {
            controller.load();
            final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(taskScheduler).scheduleAtFixedRate(task.capture(), any(Instant.class), any(Duration.class));
            task.getValue().run();
        }
    }

    @Nested
    class GetAuthedClientByLogin {
        @Test
        void getAuthedClientByLogin_shouldReturnClientOfThatLogin() {
            final LoginClient expected = authedClient(ACCOUNT_ID, "first");
            authedClient(UUID.fromString("0190a6b2-0000-7000-8000-000000000002"), "second");

            final LoginClient actual = controller.getAuthedClientByLogin("first");

            assertThat(actual).isSameAs(expected);
        }

        @Test
        void getAuthedClientByLogin_shouldReturnNull_whenNoClientHasThatLogin() {
            authedClient(ACCOUNT_ID, "first");

            assertThat(controller.getAuthedClientByLogin("other")).isNull();
        }

        private LoginClient authedClient(UUID accountId, String login) {
            final LoginClient client = mock(LoginClient.class);
            when(client.getAccount()).thenReturn(login);
            controller.loginServerClients.put(accountId, client);
            return client;
        }
    }

    @Nested
    class RemoveAuthedLoginClient {
        @Test
        void removeAuthedLoginClientIfCurrent_shouldKeepTheNewerClient_whenTheOldConnectionClosesLate() {
            final LoginClient old = clientOf(ACCOUNT_ID);
            final LoginClient newer = clientOf(ACCOUNT_ID);
            controller.loginServerClients.put(ACCOUNT_ID, old);
            controller.removeAuthedLoginClient(ACCOUNT_ID);
            controller.loginServerClients.put(ACCOUNT_ID, newer);

            final boolean actual = controller.removeAuthedLoginClientIfCurrent(old);

            assertThat(actual).isFalse();
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isSameAs(newer);
        }

        @Test
        void removeAuthedLoginClientIfCurrent_shouldRemoveTheClient_whenItStillHoldsTheAccount() {
            final LoginClient client = clientOf(ACCOUNT_ID);
            controller.loginServerClients.put(ACCOUNT_ID, client);

            final boolean actual = controller.removeAuthedLoginClientIfCurrent(client);

            assertThat(actual).isTrue();
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        @Test
        void removeAuthedLoginClientIfCurrent_shouldDoNothing_whenTheClientNeverAuthenticated() {
            final LoginClient authed = clientOf(ACCOUNT_ID);
            controller.loginServerClients.put(ACCOUNT_ID, authed);

            controller.removeAuthedLoginClientIfCurrent(clientOf(null));

            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isSameAs(authed);
        }

        @Test
        void removeAuthedLoginClient_shouldRemoveWhoeverHoldsTheAccount_whenGivenAnAccountId() {
            controller.loginServerClients.put(ACCOUNT_ID, clientOf(ACCOUNT_ID));

            controller.removeAuthedLoginClient(ACCOUNT_ID);

            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        private LoginClient clientOf(UUID accountId) {
            final LoginClient client = mock(LoginClient.class);
            when(client.getAccountId()).thenReturn(accountId);
            return client;
        }
    }

    @Nested
    class TryCheckinAccount {
        private final AccountInfo info = AccountInfo.builder()
                .id(ACCOUNT_ID)
                .login("first")
                .passwordHash("hash")
                .accessLevel(0)
                .lastServer(1)
                .build();

        @Test
        void tryCheckinAccount_shouldLeaveNoEntry_whenTheConnectionClosedBeforeTheCheckin() {
            final LoginClient client = loginClient();
            closeAndRelease(client);

            final LoginResult actual = controller.tryCheckinAccount(client, "203.0.113.7", info);

            assertThat(actual).isEqualTo(LoginResult.AUTH_SUCCESS);
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
            assertThat(controller.tryCheckinAccount(loginClient(), "203.0.113.7", info))
                    .isEqualTo(LoginResult.AUTH_SUCCESS);
        }

        @Test
        void tryCheckinAccount_shouldLetTheCloseRemoveTheEntry_whenTheConnectionClosesAfterTheCheckin() {
            final LoginClient client = loginClient();
            controller.tryCheckinAccount(client, "203.0.113.7", info);
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isSameAs(client);

            closeAndRelease(client);

            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        @Test
        void tryCheckinAccount_shouldKeepTheHolder_whenTheAccountIsAlreadyOnTheLoginServer() {
            final LoginClient holder = loginClient();
            controller.tryCheckinAccount(holder, "203.0.113.7", info);
            final LoginClient second = loginClient();

            final LoginResult actual = controller.tryCheckinAccount(second, "203.0.113.7", info);
            closeAndRelease(second);

            assertThat(actual).isEqualTo(LoginResult.ALREADY_ON_LS);
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isSameAs(holder);
        }

        private LoginClient loginClient() {
            return new LoginClient(
                    new EmbeddedChannel(),
                    LoginVariant.C621,
                    LoginKeys.builder().blowfishKey(new byte[16]).build(),
                    1,
                    null,
                    null);
        }

        private void closeAndRelease(LoginClient client) {
            client.closeNow();
            controller.removeAuthedLoginClientIfCurrent(client);
        }
    }

    @Nested
    class RequestCharacters {
        @Test
        void requestCharacters_shouldAskAndAwaitOnlyTheAuthedServersWithALink_thatTheClientCanEnter() {
            final LoginClient client = mock(LoginClient.class);
            when(client.getAccountId()).thenReturn(ACCOUNT_ID);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            final GameServerLink link = mock(GameServerLink.class);
            final GameServerInfo linked = server(1, "high-five", link, true);
            final GameServerInfo linkGone = server(2, "high-five", null, true);
            final GameServerInfo notAuthed = server(3, "high-five", mock(GameServerLink.class), false);
            final GameServerLink otherChronicleLink = mock(GameServerLink.class);
            final GameServerInfo otherChronicle = server(4, "essence", otherChronicleLink, true);

            controller.requestCharacters(client, List.of(linked, linkGone, notAuthed, otherChronicle));

            final ArgumentCaptor<Collection<Integer>> awaited = ArgumentCaptor.captor();
            verify(client).awaitCharacters(awaited.capture());
            assertThat(awaited.getValue()).containsExactly(1);
            verify(link).requestCharacters(ACCOUNT_ID);
            verify(otherChronicleLink, never()).requestCharacters(any());
        }
    }

    @Nested
    class IsLoginPossible {
        private final LoginClient client = mock(LoginClient.class);

        @BeforeEach
        void setUp() {
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getAccountId()).thenReturn(ACCOUNT_ID);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"high-five, true", "essence, false", ", false"})
        void isLoginPossible_shouldAdmitOnlyServersOfTheClientsChronicles(String chronicle, boolean expected) {
            final GameServerInfo server = spy(server(1, chronicle, mock(GameServerLink.class), true));
            doReturn(true).when(server).canLogin(client);
            gameServers.register(1, server);

            final boolean actual = controller.isLoginPossible(client, 1);

            assertThat(actual).isEqualTo(expected);
        }
    }

    private static GameServerInfo server(int id, String chronicle, GameServerLink link, boolean authed) {
        final GameServerInfo info = new GameServerInfo(id, new byte[] {(byte) id});
        info.setChronicle(chronicle);
        info.setGameServerLink(link);
        info.setAuthed(authed);
        return info;
    }

    @Nested
    class SetCharactersOnServer {
        @Test
        void setCharactersOnServer_shouldStoreTheCounts_thenReportTheAnswer() {
            final LoginClient client = mock(LoginClient.class);
            controller.loginServerClients.put(ACCOUNT_ID, client);

            controller.setCharactersOnServer(ACCOUNT_ID, 3, new long[0], 1);

            final InOrder order = inOrder(client);
            order.verify(client).setCharsOnServ(1, 3);
            order.verify(client).charactersArrived(1);
        }

        @Test
        void setCharactersOnServer_shouldReportTheAnswer_whenTheAccountHasNoCharacters() {
            final LoginClient client = mock(LoginClient.class);
            controller.loginServerClients.put(ACCOUNT_ID, client);

            controller.setCharactersOnServer(ACCOUNT_ID, 0, new long[0], 1);

            verify(client).charactersArrived(1);
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldCancelPurge() throws Exception {
            controller.load();

            controller.shutdown();

            verify(purgeTask).cancel(false);
        }
    }
}
