/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gslink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.account.AccountInfo;
import app.l2nx.login.account.AccountRepository;
import app.l2nx.login.account.AccountService;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.GameServerRegistry;
import app.l2nx.login.gameserver.GameServerRepository;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gameserver.model.ListingStatus;
import app.l2nx.login.gameserver.model.Registration;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.LoginKeys;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.LoginResult;
import app.l2nx.login.session.model.SessionKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.TaskScheduler;

class GameServerLinkTest {
    static final byte[] HEX_ID = {0x11, 0x22, 0x33, 0x44};
    static final String SERVER_NAMES = """
        <servers_list>
        \t<server id="1" name="Bartz" />
        \t<server id="2" name="Sieghardt" />
        </servers_list>
        """;
    private static final UUID ACCOUNT = UUID.fromString("0192f0a0-0000-7000-8000-000000000001");
    private static final UUID REQUEST = UUID.fromString("0192f0a0-0000-7000-8000-0000000000aa");

    @TempDir
    Path datapackRoot;

    private final LoginController loginController = mock(LoginController.class);
    private final AccountService accounts = mock(AccountService.class);
    private final GameServerRepository repository = mock(GameServerRepository.class);
    private final RecordingStreamObserver<LoginServerMessage> responses = new RecordingStreamObserver<>();
    private GameServerTable table;

    @BeforeEach
    void loadTable() throws Exception {
        Files.createDirectories(datapackRoot.resolve("data"));
        Files.writeString(datapackRoot.resolve("data/servername.xml"), SERVER_NAMES);
        table = new GameServerTable(repository, TestLoginProperties.create(datapackRoot.toFile()));
        table.load();
    }

    @Nested
    class Register {
        @Test
        void register_shouldTakeAFreeId_andAnswerWithItsName() throws Exception {
            final GameServerLink link = link(true);

            link.onNext(register(1, HEX_ID, false, "high-five"));

            final GsLinkProto.RegistrationResult actual = responses.next().getRegistrationResult();
            assertThat(actual.getServerId()).isEqualTo(1);
            assertThat(actual.getServerName()).isEqualTo("Bartz");
            final GameServerInfo info = table.getRegisteredGameServerById(1);
            assertThat(info.isAuthed()).isTrue();
            assertThat(info.getChronicle()).isEqualTo("high-five");
            assertThat(info.getPort()).isEqualTo(7777);
            assertThat(info.getMaxPlayers()).isEqualTo(100);
            assertThat(info.getGameServerLink()).isSameAs(link);
            verify(repository).create(1, HEX_ID);
            verify(repository).updateChronicle(1, "high-five");
        }

        @Test
        void register_shouldBeRefused_whileALiveLinkHoldsTheHexId() throws Exception {
            final GameServerLink live = registered();
            final RecordingStreamObserver<LoginServerMessage> duplicateResponses = new RecordingStreamObserver<>();

            link(duplicateResponses, true).onNext(register(1, HEX_ID, false, "high-five"));

            final Status actual = duplicateResponses.awaitEnd();
            assertThat(actual.getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
            assertThat(actual.getDescription()).contains("live GS-link");
            final GameServerInfo info = table.getRegisteredGameServerById(1);
            assertThat(info.getGameServerLink()).isSameAs(live);
            assertThat(info.isAuthed()).isTrue();
        }

        @Test
        void register_shouldSucceed_afterTheLinkHoldingTheHexIdEnded() throws Exception {
            registered().onError(Status.UNAVAILABLE.asRuntimeException());
            final RecordingStreamObserver<LoginServerMessage> nextResponses = new RecordingStreamObserver<>();
            final GameServerLink next = link(nextResponses, true);

            next.onNext(register(1, HEX_ID, false, "high-five"));

            assertThat(nextResponses.next().getRegistrationResult().getServerId())
                    .isEqualTo(1);
            assertThat(table.getRegisteredGameServerById(1).getGameServerLink()).isSameAs(next);
        }

        @Test
        void register_shouldNotStoreTheChronicleAgain_whenItIsUnchanged() throws Exception {
            registered().onCompleted();

            link(new RecordingStreamObserver<>(), true).onNext(register(1, HEX_ID, false, "high-five"));

            verify(repository, times(1)).updateChronicle(1, "high-five");
        }

        @Test
        void register_shouldStoreTheNewChronicle_whenTheServerChangedIt() throws Exception {
            registered().onCompleted();
            final RecordingStreamObserver<LoginServerMessage> nextResponses = new RecordingStreamObserver<>();

            link(nextResponses, true).onNext(register(1, HEX_ID, false, "essence"));

            nextResponses.next();
            verify(repository).updateChronicle(1, "essence");
            assertThat(table.getRegisteredGameServerById(1).getChronicle()).isEqualTo("essence");
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 65})
        void register_shouldBeRefused_whenTheHexIdSizeIsOutOfRange(int size) throws Exception {
            link(true).onNext(register(1, new byte[size], false, "high-five"));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(table.getRegisteredGameServerById(1)).isNull();
        }

        @Test
        void register_shouldBeRefused_whenItCarriesMoreThan32Addresses() throws Exception {
            link(true).onNext(withAddresses(register(1, HEX_ID, false, "high-five"), 33));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(table.getRegisteredGameServerById(1)).isNull();
        }

        @Test
        void register_shouldBeAccepted_atTheLimits() throws Exception {
            final byte[] hexId = new byte[64];
            hexId[0] = 0x11;

            link(true).onNext(withAddresses(register(1, hexId, false, "high-five"), 32));

            assertThat(responses.next().getRegistrationResult().getServerId()).isEqualTo(1);
            assertThat(table.getRegisteredGameServerById(1).getServerAddresses())
                    .hasSize(32);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "NOT_ACCEPTED, PERMISSION_DENIED",
            "HEX_ID_MISMATCH, PERMISSION_DENIED",
            "ID_TAKEN, ALREADY_EXISTS",
            "NO_FREE_ID, RESOURCE_EXHAUSTED",
        })
        void register_shouldEndTheStreamWithTheStatusOfTheRefusal(Registration.Reason reason, Status.Code expected)
                throws Exception {
            final GameServerRegistry registry = mock(GameServerRegistry.class);
            when(registry.register(eq(1), any(), anyBoolean()))
                    .thenReturn(Registration.Refused.builder()
                            .reason(reason)
                            .detail("refused for the test")
                            .build());

            link(responses, registry).onNext(register(1, HEX_ID, false, "high-five"));

            final Status actual = responses.awaitEnd();
            assertThat(actual.getCode()).isEqualTo(expected);
            assertThat(actual.getDescription()).contains("refused for the test");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "  "})
        void register_shouldBeRefused_whenTheChronicleIsBlank(String chronicle) throws Exception {
            link(true).onNext(register(1, HEX_ID, false, chronicle));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void onNext_shouldEndTheStream_whenTheFirstMessageIsNotRegister() throws Exception {
            link(true).onNext(accountLeft(ACCOUNT));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        }
    }

    @Nested
    class Accounts {
        @Test
        void accountsEntered_shouldHoldTheAccount_andForgetItsLoginSession() throws Exception {
            final GameServerLink link = registered();

            link.onNext(accountsEntered(ACCOUNT));

            assertThat(link.hasAccountOnGameServer(ACCOUNT)).isTrue();
            assertThat(link.getPlayerCount()).isEqualTo(1);
            verify(loginController).removeAuthedLoginClient(ACCOUNT);
        }

        @Test
        void accountLeft_shouldReleaseTheAccount() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));

            link.onNext(accountLeft(ACCOUNT));

            assertThat(link.hasAccountOnGameServer(ACCOUNT)).isFalse();
        }

        @Test
        void onNext_shouldEndTheStream_whenAnAccountIdIsMalformed() throws Exception {
            final GameServerLink link = registered();

            link.onNext(GameServerMessage.newBuilder()
                    .setAccountLeft(GsLinkProto.AccountLeft.newBuilder().setAccountId("not-a-uuid"))
                    .build());

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void kickPlayer_shouldSendKickAccount() throws Exception {
            final GameServerLink link = registered();

            link.kickPlayer(ACCOUNT);

            assertThat(responses.next().getKickAccount().getAccountId()).isEqualTo(ACCOUNT.toString());
        }

        @Test
        void requestCharacters_shouldSendReportCharacters() throws Exception {
            final GameServerLink link = registered();

            link.requestCharacters(ACCOUNT);

            assertThat(responses.next().getReportCharacters().getAccountId()).isEqualTo(ACCOUNT.toString());
        }

        @Test
        void characterReport_shouldPassTheDeleteTimesInEpochMillis() throws Exception {
            final GameServerLink link = registered();
            final Instant deleteTime = Instant.parse("2026-10-20T12:00:00.250Z");

            link.onNext(GameServerMessage.newBuilder()
                    .setCharacterReport(GsLinkProto.CharacterReport.newBuilder()
                            .setAccountId(ACCOUNT.toString())
                            .setCount(3)
                            .addDeleteTimes(Timestamp.newBuilder()
                                    .setSeconds(deleteTime.getEpochSecond())
                                    .setNanos(deleteTime.getNano())))
                    .build());

            verify(loginController).setCharactersOnServer(ACCOUNT, 3, new long[] {deleteTime.toEpochMilli()}, 1);
        }
    }

    @Nested
    class AccountRequests {
        @Test
        void changeAccessLevel_shouldPassTheLevelAndTheServer() throws Exception {
            final GameServerLink link = registered();

            link.onNext(GameServerMessage.newBuilder()
                    .setChangeAccessLevel(GsLinkProto.ChangeAccessLevel.newBuilder()
                            .setAccountId(ACCOUNT.toString())
                            .setLevel(-1))
                    .build());

            verify(accounts).changeAccessLevel(ACCOUNT, -1, 1);
        }

        @ParameterizedTest(name = "on the server: {0}")
        @ValueSource(booleans = {true, false})
        void requestTempBan_shouldPassTheBan_andWhetherTheAccountIsOnTheServer(boolean onServer) throws Exception {
            final GameServerLink link = registered();
            if (onServer) {
                link.onNext(accountsEntered(ACCOUNT));
            }

            link.onNext(tempBan(" 10.0.0.7 ", Duration.ofMinutes(30)));

            verify(accounts).tempBan(ACCOUNT, Duration.ofMinutes(30), "10.0.0.7", onServer, 1);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "  "})
        void requestTempBan_shouldPassNoAddress_whenItIsBlank(String ip) throws Exception {
            final GameServerLink link = registered();

            link.onNext(tempBan(ip, Duration.ofMinutes(30)));

            verify(accounts).tempBan(ACCOUNT, Duration.ofMinutes(30), null, false, 1);
        }

        @Test
        void reportRoute_shouldPassTrimmedHops_blankOnesAsNull() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));

            link.onNext(GameServerMessage.newBuilder()
                    .setReportRoute(GsLinkProto.ReportRoute.newBuilder()
                            .setAccountId(ACCOUNT.toString())
                            .setClientAddress(" 10.0.0.1 ")
                            .addAllHops(List.of(" 10.0.0.2", " ", "10.0.0.4 ")))
                    .build());

            verify(accounts).reportRoute(ACCOUNT, "10.0.0.1", Arrays.asList("10.0.0.2", null, "10.0.0.4"), true, 1);
        }

        @Test
        void changePassword_shouldAnswerWithTheTextOfTheAccountService() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));
            when(accounts.changePassword(ACCOUNT, "Hero", "old-pass", "new-pass", true, 1))
                    .thenReturn("You have successfully changed your password!");

            link.onNext(changePassword());

            final GsLinkProto.PasswordChangeResult actual = responses.next().getPasswordChangeResult();
            assertThat(actual.getCharacterName()).isEqualTo("Hero");
            assertThat(actual.getMessage()).isEqualTo("You have successfully changed your password!");
            assertThat(actual.getRequestId()).isEqualTo(REQUEST.toString());
        }

        @Test
        void changePassword_shouldPassThatTheAccountIsNotOnTheServer() throws Exception {
            final GameServerLink link = registered();
            when(accounts.changePassword(ACCOUNT, "Hero", "old-pass", "new-pass", false, 1))
                    .thenReturn("The password change was unsuccessful!");

            link.onNext(changePassword());

            assertThat(responses.next().getPasswordChangeResult().getMessage())
                    .isEqualTo("The password change was unsuccessful!");
        }

        private static GameServerMessage tempBan(String ip, Duration duration) {
            return GameServerMessage.newBuilder()
                    .setRequestTempBan(GsLinkProto.RequestTempBan.newBuilder()
                            .setAccountId(ACCOUNT.toString())
                            .setIp(ip)
                            .setDuration(com.google.protobuf.Duration.newBuilder()
                                    .setSeconds(duration.getSeconds())
                                    .setNanos(duration.getNano())))
                    .build();
        }

        private static GameServerMessage changePassword() {
            return GameServerMessage.newBuilder()
                    .setChangePassword(GsLinkProto.ChangePassword.newBuilder()
                            .setRequestId(REQUEST.toString())
                            .setAccountId(ACCOUNT.toString())
                            .setCharacterName("Hero")
                            .setOldPassword("old-pass")
                            .setNewPassword("new-pass"))
                    .build();
        }
    }

    @Nested
    class Authentication {
        private static final SessionKey ISSUED = SessionKey.builder()
                .loginOk1(11)
                .loginOk2(12)
                .playOk1(21)
                .playOk2(22)
                .build();
        private static final byte[] SECOND_HEX_ID = {0x55, 0x66, 0x77, 0x08};

        private LoginController sessions;
        private GameServerRegistry registry;

        @BeforeEach
        void createSessions() {
            final LoginProperties properties = TestLoginProperties.create(datapackRoot.toFile());
            sessions = new LoginController(mock(AccountRepository.class), table, mock(TaskScheduler.class));
            registry = new GameServerRegistry(table, repository, properties);
        }

        @ParameterizedTest(name = "showLicence {0}, loginOk correct {1}, playOk correct {2} -> accepted {3}")
        @CsvSource({
            "true, true, true, true",
            "true, false, true, false",
            "false, false, true, true",
            "false, true, true, true",
            "true, true, false, false",
            "false, true, false, false",
        })
        void authenticate_shouldCheckThePlayOkPair_andTheLoginPairOnlyWithTheLicence(
                boolean showLicence, boolean loginOkCorrect, boolean playOkCorrect, boolean expected) throws Exception {
            final GameServerLink link = registered(1, HEX_ID, showLicence);
            final LoginClient client = playOk(1);

            link.onNext(authenticate(ISSUED.toBuilder()
                    .loginOk1(loginOkCorrect ? 11 : 0)
                    .playOk1(playOkCorrect ? 21 : 0)
                    .build()));

            final GsLinkProto.AuthenticationResult actual = responses.next().getAuthenticationResult();
            assertThat(actual.getAccepted()).isEqualTo(expected);
            assertThat(actual.getLogin()).isEqualTo("hero");
            assertThat(actual.getRequestId()).isEqualTo(REQUEST.toString());
            if (expected) {
                assertThat(actual.getAccountId()).isEqualTo(ACCOUNT.toString());
                assertThat(sessions.getAuthedClient(ACCOUNT))
                        .as("the key is redeemed")
                        .isNull();
            } else {
                assertThat(actual.hasAccountId()).isFalse();
                assertThat(sessions.getAuthedClient(ACCOUNT)).isSameAs(client);
            }
        }

        @Test
        void authenticate_shouldRefuseTheSecondUse_ofOneKey() throws Exception {
            final GameServerLink link = registered(1, HEX_ID, true);
            playOk(1);
            link.onNext(authenticate(ISSUED));
            assertThat(responses.next().getAuthenticationResult().getAccepted()).isTrue();

            link.onNext(authenticate(ISSUED));

            final GsLinkProto.AuthenticationResult actual = responses.next().getAuthenticationResult();
            assertThat(actual.getAccepted()).isFalse();
            assertThat(actual.hasAccountId()).isFalse();
        }

        @Test
        void authenticate_shouldRefuse_whenTheClientNeverGotPlayOk() throws Exception {
            final GameServerLink link = registered(1, HEX_ID, true);
            final LoginClient client = loggedIn();

            link.onNext(authenticate(ISSUED));

            assertThat(responses.next().getAuthenticationResult().getAccepted()).isFalse();
            assertThat(sessions.getAuthedClient(ACCOUNT)).isSameAs(client);
        }

        @Test
        void authenticate_shouldRefuse_whenPlayOkWasForAnotherServer_andLeaveTheKeyToTheSelectedOne() throws Exception {
            final GameServerLink serverOne = registered(1, HEX_ID, true);
            final GameServerLink serverTwo = registered(2, SECOND_HEX_ID, true);
            playOk(1);

            serverTwo.onNext(authenticate(ISSUED));
            final GsLinkProto.AuthenticationResult refused = responses.next().getAuthenticationResult();
            serverOne.onNext(authenticate(ISSUED));
            final GsLinkProto.AuthenticationResult accepted = responses.next().getAuthenticationResult();

            assertThat(refused.getAccepted()).isFalse();
            assertThat(accepted.getAccepted()).isTrue();
            assertThat(accepted.getAccountId()).isEqualTo(ACCOUNT.toString());
        }

        @Test
        void authenticate_shouldEchoTheRequestId_whenNoClientHasTheLogin() throws Exception {
            final GameServerLink link = registered(1, HEX_ID, true);

            link.onNext(GameServerMessage.newBuilder()
                    .setAuthenticate(GsLinkProto.Authenticate.newBuilder()
                            .setLogin("nobody")
                            .setKey(GsLinkProto.SessionKey.getDefaultInstance())
                            .setRequestId(REQUEST.toString()))
                    .build());

            final GsLinkProto.AuthenticationResult actual = responses.next().getAuthenticationResult();
            assertThat(actual.getAccepted()).isFalse();
            assertThat(actual.getRequestId()).isEqualTo(REQUEST.toString());
        }

        private GameServerLink registered(int serverId, byte[] hexId, boolean checkLoginPair) throws Exception {
            final GameServerLink link =
                    new GameServerLink(responses, registry, table, sessions, accounts, checkLoginPair, ended -> {});
            link.onNext(register(serverId, hexId, false, "high-five"));
            assertThat(responses.next().getRegistrationResult().getServerId()).isEqualTo(serverId);
            return link;
        }

        private LoginClient loggedIn() {
            final LoginClient client = new LoginClient(
                    new EmbeddedChannel(),
                    LoginVariant.C621,
                    LoginKeys.builder().blowfishKey(new byte[16]).build(),
                    1,
                    null,
                    null);
            client.setAccount("hero");
            assertThat(sessions.tryCheckinAccount(
                            client,
                            "203.0.113.7",
                            AccountInfo.builder()
                                    .id(ACCOUNT)
                                    .login("hero")
                                    .passwordHash("hash")
                                    .accessLevel(0)
                                    .lastServer(1)
                                    .build()))
                    .isEqualTo(LoginResult.AUTH_SUCCESS);
            client.setSessionKey(ISSUED);
            return client;
        }

        private LoginClient playOk(int serverId) {
            final LoginClient client = loggedIn();
            client.setJoinedServerId(serverId);
            return client;
        }

        private GameServerMessage authenticate(SessionKey key) {
            return GameServerMessage.newBuilder()
                    .setAuthenticate(GsLinkProto.Authenticate.newBuilder()
                            .setLogin("hero")
                            .setKey(GsLinkProto.SessionKey.newBuilder()
                                    .setLoginOk1(key.loginOk1())
                                    .setLoginOk2(key.loginOk2())
                                    .setPlayOk1(key.playOk1())
                                    .setPlayOk2(key.playOk2()))
                            .setRequestId(REQUEST.toString()))
                    .build();
        }
    }

    @Nested
    class PublishListing {
        @Test
        void publishListing_shouldUpdateOnlyTheAttributesSet() throws Exception {
            final GameServerLink link = registered();

            link.onNext(listing(GsLinkProto.PublishListing.newBuilder()
                    .setType(0x01)
                    .setSquareBracket(true)
                    .setMaxPlayers(250)
                    .setAge(18)));

            final GameServerInfo actual = table.getRegisteredGameServerById(1);
            assertThat(actual.getServerType()).isEqualTo(0x01);
            assertThat(actual.isShowingBrackets()).isTrue();
            assertThat(actual.getMaxPlayers()).isEqualTo(250);
            assertThat(actual.getAgeLimit()).isEqualTo(18);
            assertThat(actual.getStatus()).isEqualTo(ListingStatus.DOWN);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = GsLinkProto.ListingStatus.class, names = "UNRECOGNIZED", mode = EnumSource.Mode.EXCLUDE)
        void publishListing_shouldMapEveryKnownStatus(GsLinkProto.ListingStatus status) throws Exception {
            registered().onNext(listing(GsLinkProto.PublishListing.newBuilder().setStatus(status)));

            assertThat(table.getRegisteredGameServerById(1).getStatus().name())
                    .isEqualTo(status.name().substring("LISTING_STATUS_".length()));
        }

        @Test
        void publishListing_shouldApplyNothing_whenTheStatusIsUnknown() throws Exception {
            final GameServerLink link = registered();

            link.onNext(listing(
                    GsLinkProto.PublishListing.newBuilder().setStatusValue(42).setMaxPlayers(250)));

            final GameServerInfo actual = table.getRegisteredGameServerById(1);
            assertThat(actual.getStatus()).isEqualTo(ListingStatus.DOWN);
            assertThat(actual.getMaxPlayers()).isEqualTo(100);
            assertThat(link.isAuthed()).as("the stream stays open").isTrue();
        }

        private static GameServerMessage listing(GsLinkProto.PublishListing.Builder listing) {
            return GameServerMessage.newBuilder().setPublishListing(listing).build();
        }
    }

    @Test
    void onCompleted_shouldMarkTheServerDown_andEndTheStream() throws Exception {
        final GameServerLink link = registered();

        link.onCompleted();

        assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.OK);
        final GameServerInfo actual = table.getRegisteredGameServerById(1);
        assertThat(actual.isAuthed()).isFalse();
        assertThat(actual.getGameServerLink()).isNull();
        verifyNoMoreInteractions(loginController, accounts);
    }

    private GameServerLink link(boolean acceptNew) {
        return link(responses, acceptNew);
    }

    private GameServerLink link(RecordingStreamObserver<LoginServerMessage> observer, boolean acceptNew) {
        final LoginProperties properties = TestLoginProperties.create(datapackRoot.toFile()).toBuilder()
                .gameServer(LoginProperties.GameServer.builder()
                        .acceptNew(acceptNew)
                        .build())
                .build();
        return link(observer, new GameServerRegistry(table, repository, properties));
    }

    private GameServerLink link(RecordingStreamObserver<LoginServerMessage> observer, GameServerRegistry registry) {
        return new GameServerLink(observer, registry, table, loginController, accounts, false, ended -> {});
    }

    private GameServerLink registered() throws Exception {
        final GameServerLink link = link(true);
        link.onNext(register(1, HEX_ID, false, "high-five"));
        responses.next();
        return link;
    }

    static GameServerMessage register(int serverId, byte[] hexId, boolean acceptAlternativeId, String chronicle) {
        return GameServerMessage.newBuilder()
                .setRegister(GsLinkProto.Register.newBuilder()
                        .setServerId(serverId)
                        .setAcceptAlternativeId(acceptAlternativeId)
                        .setHexId(ByteString.copyFrom(hexId))
                        .setChronicle(chronicle)
                        .setPort(7777)
                        .setMaxPlayers(100)
                        .addAddresses(GsLinkProto.GameServerAddress.newBuilder()
                                .setSubnet("0.0.0.0/0")
                                .setAddress("127.0.0.1")))
                .build();
    }

    private static GameServerMessage withAddresses(GameServerMessage message, int count) {
        final GsLinkProto.Register.Builder register =
                message.getRegister().toBuilder().clearAddresses();
        for (int i = 0; i < count; i++) {
            register.addAddresses(GsLinkProto.GameServerAddress.newBuilder()
                    .setSubnet("10.0." + i + ".0/24")
                    .setAddress("10.0." + i + ".1"));
        }
        return GameServerMessage.newBuilder().setRegister(register).build();
    }

    static GameServerMessage accountsEntered(UUID accountId) {
        return GameServerMessage.newBuilder()
                .setAccountsEntered(GsLinkProto.AccountsEntered.newBuilder().addAccountIds(accountId.toString()))
                .build();
    }

    static GameServerMessage accountLeft(UUID accountId) {
        return GameServerMessage.newBuilder()
                .setAccountLeft(GsLinkProto.AccountLeft.newBuilder().setAccountId(accountId.toString()))
                .build();
    }
}
