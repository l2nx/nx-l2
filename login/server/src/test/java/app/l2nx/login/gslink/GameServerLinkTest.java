/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gslink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.PasswordHash;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.repository.GameServerRepository;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class GameServerLinkTest {
    static final byte[] HEX_ID = {0x11, 0x22, 0x33, 0x44};
    static final String SERVER_NAMES = """
            <servers_list>
            \t<server id="1" name="Bartz" />
            \t<server id="2" name="Sieghardt" />
            </servers_list>
            """;
    private static final UUID ACCOUNT = UUID.fromString("0192f0a0-0000-7000-8000-000000000001");

    @TempDir
    Path datapackRoot;

    private final LoginController loginController = mock(LoginController.class);
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

        @Test
        void register_shouldBeRefused_whenNewServersAreNotAccepted() throws Exception {
            link(false).onNext(register(1, HEX_ID, false, "high-five"));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
            assertThat(table.getRegisteredGameServerById(1)).isNull();
        }

        @Test
        void register_shouldGiveTheNextFreeId_whenTheHexIdDiffersAndAnotherIdIsAccepted() throws Exception {
            table.register(1, new GameServerInfo(1, new byte[] {9}));

            link(true).onNext(register(1, HEX_ID, true, "high-five"));

            assertThat(responses.next().getRegistrationResult().getServerId()).isEqualTo(2);
        }

        @Test
        void register_shouldBeRefused_whenTheHexIdDiffersAndNoOtherIdIsAccepted() throws Exception {
            table.register(1, new GameServerInfo(1, new byte[] {9}));

            link(true).onNext(register(1, HEX_ID, false, "high-five"));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
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
    class TempBan {
        @Test
        void requestTempBan_shouldBanAccountAndAddress_whenTheLengthIsPositive() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));

            link.onNext(tempBan(ACCOUNT, "10.0.0.7", Duration.ofMinutes(30)));

            verify(loginController).setAccountTempBan(ACCOUNT, Duration.ofMinutes(30));
            verify(loginController)
                    .addBanForAddress("10.0.0.7", Duration.ofMinutes(30).toMillis());
        }

        @ParameterizedTest(name = "{0} minutes")
        @ValueSource(longs = {0, -5})
        void requestTempBan_shouldBanNothing_whenTheLengthIsNotPositive(long minutes) throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));

            link.onNext(tempBan(ACCOUNT, "10.0.0.7", Duration.ofMinutes(minutes)));

            verify(loginController, never()).setAccountTempBan(eq(ACCOUNT), eq(Duration.ofMinutes(minutes)));
            verify(loginController, never())
                    .addBanForAddress(
                            anyString(), eq(Duration.ofMinutes(minutes).toMillis()));
        }

        @Test
        void requestTempBan_shouldBanNothing_whenTheAccountIsNotOnThisServer() throws Exception {
            final GameServerLink link = registered();

            link.onNext(tempBan(ACCOUNT, null, Duration.ofMinutes(30)));

            verify(loginController, never()).setAccountTempBan(eq(ACCOUNT), eq(Duration.ofMinutes(30)));
        }
    }

    @Nested
    class Password {
        @Test
        void changePassword_shouldAnswerChanged_whenTheStoreAcceptsTheNewHash() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));
            when(loginController.changePassword(eq(ACCOUNT), anyString(), anyString()))
                    .thenReturn(true);

            link.onNext(changePassword("old-pass", "new-pass"));

            final GsLinkProto.PasswordChangeResult actual = responses.next().getPasswordChangeResult();
            assertThat(actual.getCharacterName()).isEqualTo("Hero");
            assertThat(actual.getMessage()).isEqualTo("You have successfully changed your password!");
        }

        @Test
        void changePassword_shouldPassTheStoredHashForm() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));

            link.onNext(changePassword("old-pass", "new-pass"));

            verify(loginController).changePassword(ACCOUNT, PasswordHash.of("old-pass"), PasswordHash.of("new-pass"));
        }

        @Test
        void changePassword_shouldAnswerInvalidData_whenAPasswordIsEmpty() throws Exception {
            final GameServerLink link = registered();
            link.onNext(accountsEntered(ACCOUNT));

            link.onNext(changePassword("", "new-pass"));

            assertThat(responses.next().getPasswordChangeResult().getMessage())
                    .isEqualTo("Invalid password data! Try again.");
        }
    }

    @Test
    void publishListing_shouldUpdateOnlyTheAttributesSet() throws Exception {
        final GameServerLink link = registered();

        link.onNext(GameServerMessage.newBuilder()
                .setPublishListing(GsLinkProto.PublishListing.newBuilder()
                        .setType(0x01)
                        .setSquareBracket(true)
                        .setMaxPlayers(250)
                        .setAge(18))
                .build());

        final GameServerInfo actual = table.getRegisteredGameServerById(1);
        assertThat(actual.getServerType()).isEqualTo(0x01);
        assertThat(actual.isShowingBrackets()).isTrue();
        assertThat(actual.getMaxPlayers()).isEqualTo(250);
        assertThat(actual.getAgeLimit()).isEqualTo(18);
    }

    @Test
    void onCompleted_shouldMarkTheServerDown_andEndTheStream() throws Exception {
        final GameServerLink link = registered();

        link.onCompleted();

        assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.OK);
        final GameServerInfo actual = table.getRegisteredGameServerById(1);
        assertThat(actual.isAuthed()).isFalse();
        assertThat(actual.getGameServerLink()).isNull();
        verifyNoMoreInteractions(loginController);
    }

    private GameServerLink link(boolean acceptNew) {
        return link(responses, acceptNew);
    }

    private GameServerLink link(RecordingStreamObserver<LoginServerMessage> observer, boolean acceptNew) {
        return new GameServerLink(
                observer, new InetSocketAddress("192.0.2.20", 40000), table, loginController, acceptNew, ended -> {});
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

    private static GameServerMessage tempBan(UUID accountId, String ip, Duration duration) {
        final GsLinkProto.RequestTempBan.Builder ban = GsLinkProto.RequestTempBan.newBuilder()
                .setAccountId(accountId.toString())
                .setDuration(com.google.protobuf.Duration.newBuilder()
                        .setSeconds(duration.getSeconds())
                        .setNanos(duration.getNano()));
        if (ip != null) {
            ban.setIp(ip);
        }
        return GameServerMessage.newBuilder().setRequestTempBan(ban).build();
    }

    private static GameServerMessage changePassword(String oldPassword, String newPassword) {
        return GameServerMessage.newBuilder()
                .setChangePassword(GsLinkProto.ChangePassword.newBuilder()
                        .setAccountId(ACCOUNT.toString())
                        .setCharacterName("Hero")
                        .setOldPassword(oldPassword)
                        .setNewPassword(newPassword))
                .build();
    }
}
