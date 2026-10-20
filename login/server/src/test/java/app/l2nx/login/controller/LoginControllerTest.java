/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.controller;

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

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.enums.LoginResult;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.repository.AccountRepository;
import app.l2nx.login.repository.GameServerRepository;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class LoginControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("0190a6b2-0000-7000-8000-000000000001");
    private static final Duration LOGIN_TIMEOUT = LoginController.LOGIN_TIMEOUT;

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> purgeTask = mock(ScheduledFuture.class);
    private final LoginController controller = new LoginController(
            mock(AccountRepository.class), TestLoginProperties.create(new File(".")), taskScheduler);

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
    class NewBlowfishKey {
        @Test
        void newBlowfishKey_shouldHaveNoZeroByte_whenTheRandomDrawsItsLowestValues() {
            final byte[] actual = LoginController.newBlowfishKey(() -> 0L);

            assertThat(actual).hasSize(16).containsOnly(1);
        }
    }

    @Nested
    class Purge {
        @Test
        void purge_shouldCloseClient_whenLoginTimedOut() throws Exception {
            final LoginClient client = client(false, System.currentTimeMillis() - LOGIN_TIMEOUT.toMillis(), true);

            runPurge();

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        @Test
        void purge_shouldCloseClient_whenDisconnected() throws Exception {
            final LoginClient client = client(true, System.currentTimeMillis(), false);

            runPurge();

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isNull();
        }

        @Test
        void purge_shouldKeepClient_whenJoinedGameServer() throws Exception {
            final LoginClient client = client(true, System.currentTimeMillis() - LOGIN_TIMEOUT.toMillis(), true);

            runPurge();

            verify(client, never()).close(any(LoginFailReason.class));
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

            controller.removeAuthedLoginClientIfCurrent(old);

            assertThat(controller.getAuthedClient(ACCOUNT_ID)).isSameAs(newer);
        }

        @Test
        void removeAuthedLoginClientIfCurrent_shouldRemoveTheClient_whenItStillHoldsTheAccount() {
            final LoginClient client = clientOf(ACCOUNT_ID);
            controller.loginServerClients.put(ACCOUNT_ID, client);

            controller.removeAuthedLoginClientIfCurrent(client);

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
        @TempDir
        private Path datapackRoot;

        private final AccountInfo info = new AccountInfo(ACCOUNT_ID, "first", "hash", 0, 1);

        @BeforeEach
        void setUp() {
            // The constructor registers itself as GameServerTable.getInstance().
            new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
        }

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
            return new LoginClient(new EmbeddedChannel(), LoginVariant.C621, null, 1, null);
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
        @TempDir
        Path datapackRoot;

        private final LoginClient client = mock(LoginClient.class);

        @BeforeEach
        void setUp() {
            // The constructor registers itself as GameServerTable.getInstance(); load() drops other tests' servers.
            new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()))
                    .load();
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getAccountId()).thenReturn(ACCOUNT_ID);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"high-five, true", "essence, false", ", false"})
        void isLoginPossible_shouldAdmitOnlyServersOfTheClientsChronicles(String chronicle, boolean expected) {
            // A spy: an open listing status needs the running login server.
            final GameServerInfo server = spy(server(1, chronicle, mock(GameServerLink.class), true));
            doReturn(true).when(server).canLogin(client);
            GameServerTable.getInstance().register(1, server);

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
    class BannedAddress {
        @Test
        void isBannedAddress_shouldMatchAnExactIpv6Ban_andNotThrow() throws Exception {
            controller.addBanForAddress("2001:db8:0:0:0:0:0:1", 0);

            assertThat(controller.isBannedAddress(InetAddress.getByName("2001:db8::1")))
                    .isTrue();
            assertThat(controller.isBannedAddress(InetAddress.getByName("2001:db8::2")))
                    .isFalse();
        }

        @ParameterizedTest(name = "ban {0}, client {1}")
        @CsvSource({"2001:db8::1, 2001:db8:0:0:0:0:0:1", "2001:db8:0:0:0:0:0:1, 2001:db8::1", "2001:DB8::1, 2001:db8::1"
        })
        void isBannedAddress_shouldMatch_whateverIpv6FormTheBanUses(String ban, String client) {
            controller.addBanForAddress(ban, 0);

            assertThat(controller.isBannedAddress(InetAddress.ofLiteral(client)))
                    .isTrue();
        }

        @Test
        void addBanForAddress_shouldRefuseAHostName_withoutResolvingIt() {
            assertThat(controller.addBanForAddress("localhost", 0)).isFalse();
            assertThat(controller.getBannedIps()).isEmpty();
        }

        @Test
        void removeBanForAddress_shouldRemoveTheBan_givenAnotherIpv6Form() {
            controller.addBanForAddress("2001:db8::1", 0);

            assertThat(controller.removeBanForAddress("2001:db8:0:0:0:0:0:1")).isTrue();
            assertThat(controller.getBannedIps()).isEmpty();
        }

        @Test
        void isBannedAddress_shouldMatchAZeroedIpv4Subnet() throws Exception {
            controller.addBanForAddress("10.1.0.0", 0);

            assertThat(controller.isBannedAddress(InetAddress.getByName("10.1.2.3")))
                    .isTrue();
            assertThat(controller.isBannedAddress(InetAddress.getByName("10.2.2.3")))
                    .isFalse();
        }

        @Test
        void isBannedAddress_shouldDropAnExpiredSubnetBan_andCheckTheWiderOnes() throws Exception {
            controller.getBannedIps().put("10.1.2.0", System.currentTimeMillis() - 1);
            controller.addBanForAddress("10.0.0.0", 0);

            assertThat(controller.isBannedAddress(InetAddress.getByName("10.1.2.3")))
                    .isTrue();
            assertThat(controller.getBannedIps()).doesNotContainKey("10.1.2.0");
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
