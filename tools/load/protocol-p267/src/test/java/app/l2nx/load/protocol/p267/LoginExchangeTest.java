/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.intent.Login;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerList;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import app.l2nx.protocol.login.c621.crypt.Credentials;
import app.l2nx.protocol.login.c621.serverpackets.AccountKicked;
import app.l2nx.protocol.login.c621.serverpackets.GGAuth;
import app.l2nx.protocol.login.c621.serverpackets.LoginFail;
import app.l2nx.protocol.login.c621.serverpackets.LoginOk;
import app.l2nx.protocol.login.c621.serverpackets.LoginServerPacket;
import app.l2nx.protocol.login.c621.serverpackets.PlayFail;
import app.l2nx.protocol.login.c621.serverpackets.PlayOk;
import app.l2nx.protocol.login.c621.serverpackets.reason.AccountKickedReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.PlayFailReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoginExchangeTest {
    private static final Login LOGIN =
            Login.builder().account("load42").password("secret").build();

    private final RecordingListener listener = new RecordingListener();
    private final List<PlayOk> playOks = new ArrayList<>();
    private final LoginExchange exchange = new LoginExchange(LoginServerSide.SERVER_ID, listener, playOks::add);
    private final EmbeddedChannel client = connect();
    private final EmbeddedChannel server = new EmbeddedChannel();

    LoginExchangeTest() {
        LoginServerSide.install(server.pipeline());
    }

    @AfterEach
    void tearDown() {
        client.finishAndReleaseAll();
        server.finishAndReleaseAll();
    }

    @Nested
    class Handshake {
        @Test
        void channelActive_shouldReportLoginConnect() {
            assertThat(listener.events()).containsExactly("state LOGIN_CONNECT");
        }

        @Test
        void init_shouldAnswerAuthGameGuardWithTheSessionId() {
            deliver(LoginServerSide.init());

            final String actual = sentHex(LoginClientOpcode.AUTH_GAME_GUARD, 4 + 16);

            assertThat(actual).isEqualTo("4d3c2b1a" + "00".repeat(16));
            assertThat(listener.events()).containsExactly("state LOGIN_CONNECT");
        }

        @Test
        void ggAuth_shouldReportLoginAuth() {
            deliver(LoginServerSide.init());
            sent(LoginClientOpcode.AUTH_GAME_GUARD, AuthGameGuard::decode);

            deliver(new GGAuth(LoginServerSide.SESSION_ID));

            assertThat(listener.events()).containsExactly("state LOGIN_CONNECT", "state LOGIN_AUTH");
        }

        @Test
        void ggAuth_shouldFail_whenTheSessionDiffers() {
            deliver(LoginServerSide.init());
            sent(LoginClientOpcode.AUTH_GAME_GUARD, AuthGameGuard::decode);

            deliver(new GGAuth(LoginServerSide.SESSION_ID + 1));

            assertFailedWith(IllegalStateException.class, "GGAuth answered session");
        }
    }

    @Nested
    class SendLogin {
        @Test
        void login_shouldSendTheCredentialsInOneBlock_underTheModulusOfInit() throws Exception {
            authenticate();

            exchange.login(LOGIN);

            final RequestAuthLogin packet = sent(LoginClientOpcode.REQUEST_AUTH_LOGIN, RequestAuthLogin::decode);
            assertThat(packet.block2()).isNull();
            assertThat(Credentials.decrypt(LoginServerSide.RSA.getPrivate(), packet))
                    .isEqualTo(new Credentials("load42", "secret"));
        }

        @Test
        void login_shouldFail_beforeGgAuth() {
            deliver(LoginServerSide.init());

            exchange.login(LOGIN);

            assertFailedWith(IllegalStateException.class, "cannot send LOGIN");
        }

        @Test
        void login_shouldFail_whenTheAccountDoesNotFitTheBlock() {
            authenticate();

            exchange.login(LOGIN.toBuilder().account("an-account-of-20chars").build());

            assertFailedWith(IllegalArgumentException.class, "user takes up to 14 bytes");
        }

        @Test
        void loginOk_shouldAcknowledgeTheLogin_andRequestTheServerList() {
            authenticate();
            exchange.login(LOGIN);
            sent(LoginClientOpcode.REQUEST_AUTH_LOGIN, RequestAuthLogin::decode);

            deliver(LoginOk.builder()
                    .loginOk1(LoginServerSide.LOGIN_OK_1)
                    .loginOk2(LoginServerSide.LOGIN_OK_2)
                    .build());

            assertThat(listener.events()).endsWith("state LOGIN_AUTH", "ack LOGIN");
            assertThat(sent(LoginClientOpcode.REQUEST_SERVER_LIST, RequestServerList::decode))
                    .isEqualTo(RequestServerList.builder()
                            .loginOk1(LoginServerSide.LOGIN_OK_1)
                            .loginOk2(LoginServerSide.LOGIN_OK_2)
                            .build());
        }

        @Test
        void serverList_shouldAcknowledgeTheLoginBeforeTheServerList_whenLoginOkIsSkipped() {
            authenticate();
            exchange.login(LOGIN);
            sent(LoginClientOpcode.REQUEST_AUTH_LOGIN, RequestAuthLogin::decode);

            deliver(LoginServerSide.serverList(LoginServerSide.SERVER_ID));

            assertThat(listener.events()).endsWith("state LOGIN_AUTH", "ack LOGIN", "state SERVER_LIST");
        }

        @Test
        void serverList_shouldReportNoState_whenTheListenerClosesOnTheAcknowledgement() {
            listener.reacting(event -> {
                if (event.equals("ack LOGIN")) {
                    exchange.close();
                }
            });
            authenticate();
            exchange.login(LOGIN);
            sent(LoginClientOpcode.REQUEST_AUTH_LOGIN, RequestAuthLogin::decode);

            deliver(LoginServerSide.serverList(LoginServerSide.SERVER_ID));

            assertThat(listener.events()).endsWith("state LOGIN_AUTH", "ack LOGIN");
            assertThat(client.isOpen()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.load.protocol.p267.LoginExchangeTest#loginRefusals")
        void refusal_shouldRejectTheLogin_thenStop(LoginServerPacket refusal) {
            authenticate();
            exchange.login(LOGIN);
            sent(LoginClientOpcode.REQUEST_AUTH_LOGIN, RequestAuthLogin::decode);

            deliver(refusal);

            assertThat(listener.events()).endsWith("state LOGIN_AUTH", "reject LOGIN", "state STOPPED");
            assertThat(client.isOpen()).isFalse();
        }
    }

    @Nested
    class ServerListing {
        @Test
        void serverList_shouldReportServerList_whenTheServerIsListed() {
            requestServerList();

            deliver(LoginServerSide.serverList(1, LoginServerSide.SERVER_ID));

            assertThat(listener.events()).endsWith("ack LOGIN", "state SERVER_LIST");
        }

        @Test
        void serverList_shouldFail_whenTheServerIsMissing() {
            requestServerList();

            deliver(LoginServerSide.serverList(1, 3));

            assertFailedWith(IllegalStateException.class, "Game server 2 is not in the server list [1, 3]");
        }
    }

    @Nested
    class SendSelectServer {
        @Test
        void selectServer_shouldSendTheLoginOkPairAndTheServerId() {
            listServers();

            exchange.selectServer();

            assertThat(sent(LoginClientOpcode.REQUEST_SERVER_LOGIN, RequestServerLogin::decode))
                    .isEqualTo(RequestServerLogin.builder()
                            .loginOk1(LoginServerSide.LOGIN_OK_1)
                            .loginOk2(LoginServerSide.LOGIN_OK_2)
                            .serverId(LoginServerSide.SERVER_ID)
                            .build());
        }

        @Test
        void selectServer_shouldFail_beforeTheServerList() {
            requestServerList();

            exchange.selectServer();

            assertFailedWith(IllegalStateException.class, "cannot send SELECT_SERVER");
        }

        @Test
        void playOk_shouldAcknowledgeTheSelection_thenHandOverAndCloseQuietly() {
            listServers();
            exchange.selectServer();
            sent(LoginClientOpcode.REQUEST_SERVER_LOGIN, RequestServerLogin::decode);

            final PlayOk expected = PlayOk.builder()
                    .playOk1(LoginServerSide.PLAY_OK_1)
                    .playOk2(LoginServerSide.PLAY_OK_2)
                    .build();
            deliver(expected);

            assertThat(listener.events()).endsWith("state SERVER_LIST", "ack SELECT_SERVER");
            assertThat(playOks).containsExactly(expected);
            assertThat(client.isOpen()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.load.protocol.p267.LoginExchangeTest#selectionRefusals")
        void refusal_shouldRejectTheSelection_thenStop(LoginServerPacket refusal) {
            listServers();
            exchange.selectServer();
            sent(LoginClientOpcode.REQUEST_SERVER_LOGIN, RequestServerLogin::decode);

            deliver(refusal);

            assertThat(listener.events()).endsWith("state SERVER_LIST", "reject SELECT_SERVER", "state STOPPED");
            assertThat(playOks).isEmpty();
            assertThat(client.isOpen()).isFalse();
        }
    }

    @Nested
    class Errors {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.load.protocol.p267.LoginExchangeTest#undecodableFrames")
        void read_shouldFail_whenTheFrameDoesNotDecode(String name, Consumer<ByteBuf> payload) {
            deliver(LoginServerSide.init());
            sent(LoginClientOpcode.AUTH_GAME_GUARD, AuthGameGuard::decode);

            client.writeInbound(server(payload));

            assertFailedWith(PacketDecodeException.class, "");
        }

        @Test
        void read_shouldFail_whenThePacketIsUnexpectedInThePhase() {
            deliver(LoginServerSide.init());
            sent(LoginClientOpcode.AUTH_GAME_GUARD, AuthGameGuard::decode);

            deliver(PlayOk.builder().playOk1(1).playOk2(2).build());

            assertFailedWith(IllegalStateException.class, "Unexpected PlayOk");
        }

        @Test
        void channelInactive_shouldReportStopped_whenTheConnectionDrops() {
            client.close();

            assertThat(listener.events()).containsExactly("state LOGIN_CONNECT", "state STOPPED");
        }

        @Test
        void close_shouldCloseSilently_andTwice() {
            exchange.close();
            exchange.close();
            exchange.login(LOGIN);

            assertThat(client.isOpen()).isFalse();
            assertThat(listener.events()).containsExactly("state LOGIN_CONNECT");
        }

        @Test
        void connectFailed_shouldReportNothing_afterClose() {
            exchange.close();

            exchange.connectFailed(new IllegalStateException("closed while connecting"));

            assertThat(listener.events()).containsExactly("state LOGIN_CONNECT");
        }
    }

    static Stream<LoginServerPacket> loginRefusals() {
        return Stream.of(
                new LoginFail(LoginFailReason.REASON_USER_OR_PASS_WRONG),
                new AccountKicked(AccountKickedReason.REASON_PERMANENTLY_BANNED));
    }

    static Stream<LoginServerPacket> selectionRefusals() {
        return Stream.of(
                new PlayFail(PlayFailReason.REASON_SERVER_OVERLOADED),
                new LoginFail(LoginFailReason.REASON_ACCESS_FAILED),
                new AccountKicked(AccountKickedReason.REASON_GENERIC_VIOLATION));
    }

    static Stream<Arguments> undecodableFrames() {
        return Stream.of(
                Arguments.of("unknown opcode", (Consumer<ByteBuf>) out -> out.writeByte(0x55)),
                // The cipher pads every frame, so only a body far past the padding is cut short.
                Arguments.of("ServerList of 255 servers cut short", (Consumer<ByteBuf>)
                        out -> out.writeByte(0x04).writeByte(0xFF)),
                Arguments.of("unknown LoginFail reason", (Consumer<ByteBuf>)
                        out -> out.writeByte(0x01).writeByte(0x7F)));
    }

    private EmbeddedChannel connect() {
        final EmbeddedChannel channel = new EmbeddedChannel(false, false);
        exchange.install(channel, LoginServerSide.MAX_FRAME);
        try {
            channel.register();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return channel;
    }

    private void authenticate() {
        deliver(LoginServerSide.init());
        sent(LoginClientOpcode.AUTH_GAME_GUARD, AuthGameGuard::decode);
        deliver(new GGAuth(LoginServerSide.SESSION_ID));
    }

    private void requestServerList() {
        authenticate();
        exchange.login(LOGIN);
        sent(LoginClientOpcode.REQUEST_AUTH_LOGIN, RequestAuthLogin::decode);
        deliver(LoginOk.builder()
                .loginOk1(LoginServerSide.LOGIN_OK_1)
                .loginOk2(LoginServerSide.LOGIN_OK_2)
                .build());
        sent(LoginClientOpcode.REQUEST_SERVER_LIST, RequestServerList::decode);
    }

    private void listServers() {
        requestServerList();
        deliver(LoginServerSide.serverList(LoginServerSide.SERVER_ID));
    }

    private void deliver(LoginServerPacket packet) {
        client.writeInbound(server(packet::write));
    }

    private ByteBuf server(Consumer<ByteBuf> payload) {
        server.writeOutbound(LoginServerSide.frame(server.alloc(), payload));
        return server.readOutbound();
    }

    private <T> T sent(LoginClientOpcode opcode, Function<ByteBuf, T> decoder) {
        final ByteBuf payload = received();
        try {
            assertThat(LoginClientOpcode.of(payload.readUnsignedByte())).isEqualTo(opcode);
            return decoder.apply(payload);
        } finally {
            payload.release();
        }
    }

    private String sentHex(LoginClientOpcode opcode, int bodyLength) {
        return sent(
                opcode, body -> HexFormat.of().formatHex(ByteBufUtil.getBytes(body, body.readerIndex(), bodyLength)));
    }

    private ByteBuf received() {
        final ByteBuf frame = client.readOutbound();
        assertThat(frame).as("a client frame").isNotNull();
        server.writeInbound(frame);
        final ByteBuf payload = server.readInbound();
        assertThat(payload).as("a frame the server cipher accepts").isNotNull();
        return payload;
    }

    private void assertFailedWith(Class<? extends Throwable> type, String message) {
        assertThat(listener.events()).last().isEqualTo("failed");
        assertThat(listener.events()).doesNotContain("state STOPPED");
        assertThat(listener.failures())
                .singleElement(InstanceOfAssertFactories.THROWABLE)
                .isInstanceOf(type)
                .hasMessageContaining(message);
        assertThat(client.isOpen()).isFalse();
    }
}
