/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.protocol.c621.LoginRoutes;
import app.l2nx.login.protocol.c621.ServerListMapper;
import app.l2nx.login.protocol.c621.handler.AuthGameGuardHandler;
import app.l2nx.login.protocol.c621.handler.RequestAuthLoginHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerListHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerLoginHandler;
import app.l2nx.login.protocol.c621.pipeline.LoginHandshakeHandler;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.ScrambledKeyPair;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.XorChecksum;
import app.l2nx.network.exec.BlockingTaskExecutor;
import app.l2nx.network.test.TestChannels;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoginPipelineTest {
    private static final byte[] SESSION_KEY = HexFormat.of().parseHex("0102030405060708090a0b0c0d0e0f10");
    private static final int SESSION_ID = 0x0BADF00D;
    private static final UUID ACCOUNT_ID = UUID.fromString("0190a6b2-0000-7000-8000-000000000001");

    private final BlockingTaskExecutor tasks = new BlockingTaskExecutor("login-test", 2, new SimpleMeterRegistry());
    private final List<LoginClient> released = new CopyOnWriteArrayList<>();
    private final LoginRoutes routes = new LoginRoutes(
            new AuthGameGuardHandler(),
            mock(RequestAuthLoginHandler.class),
            mock(RequestServerLoginHandler.class),
            new RequestServerListHandler());
    private final ScrambledKeyPair rsa;
    private final EmbeddedChannel channel;

    LoginPipelineTest() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
        rsa = new ScrambledKeyPair(generator.generateKeyPair());
        final LoginKeys keys = new LoginKeys(rsa, SESSION_KEY);
        channel = TestChannels.embedded();
        initializer(() -> keys).accept(channel);
        channel.pipeline().fireChannelActive();
        channel.runPendingTasks();
    }

    private LoginChannelInitializer initializer(Supplier<LoginKeys> keys) {
        return new LoginChannelInitializer(
                LoginVariant.C621,
                NetworkProperties.defaults(),
                tasks,
                keys,
                new FixedRandom(SESSION_ID),
                released::add,
                routes,
                mock(ServerListMapper.class));
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
        tasks.close();
    }

    @Nested
    class Connect {
        @Test
        void connect_shouldSendInitFirst_andRemoveTheHandshake() {
            final ByteBuf init = channel.readOutbound();

            // Init: 170 bytes of payload, 8 of XOR header, 6 of padding, 8 of trailer, 2 of length.
            assertThat(init.getUnsignedShortLE(init.readerIndex())).isEqualTo(194);
            assertThat(channel.pipeline().get(LoginHandshakeHandler.class)).isNull();
            init.release();
        }

        @Test
        void connect_shouldBuildThePipelineInOrder() {
            drop(channel.readOutbound());

            assertThat(channel.pipeline().names())
                    .containsSubsequence(
                            PipelineNames.FRAME_DECODER,
                            PipelineNames.CIPHER_DECODER,
                            PipelineNames.FRAME_ENCODER,
                            PipelineNames.IDLE,
                            PipelineNames.IDLE_POLICY,
                            PipelineNames.OUTBOUND,
                            PipelineNames.DEADLINES,
                            PipelineNames.PACKET,
                            PipelineNames.TAIL);
        }

        @Test
        void handshakeTimeout_shouldClose_whenNoAuthGameGuardArrives() {
            drop(channel.readOutbound());

            channel.advanceTimeBy(10, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.TIMEOUT);
        }
    }

    @Nested
    class Keys {
        @Test
        void accept_shouldDrawTheBlowfishKeyOfEachConnection() {
            final RandomGenerator random = new SplittableRandom(42);
            final LoginChannelInitializer initializer =
                    initializer(() -> new LoginKeys(rsa, LoginKeys.newBlowfishKey(random)));
            final EmbeddedChannel first = TestChannels.embedded();
            final EmbeddedChannel second = TestChannels.embedded();
            try {
                initializer.accept(first);
                initializer.accept(second);

                assertThat(LoginClient.of(first).getBlowfishKey())
                        .isNotEqualTo(LoginClient.of(second).getBlowfishKey());
            } finally {
                first.finishAndReleaseAll();
                second.finishAndReleaseAll();
            }
        }
    }

    @Nested
    class Inbound {
        @Test
        void frame_shouldCloseWithError_whenChecksumIsBad() {
            drop(channel.readOutbound());
            final ByteBuf bad = clientFrame(new byte[] {0x07, 1, 2, 3});
            bad.setByte(3, bad.getByte(3) ^ 0x40);

            channel.writeInbound(bad);

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
        }

        @Test
        void unknownOpcode_shouldBeDroppedAndCounted() {
            drop(channel.readOutbound());

            channel.writeInbound(clientFrame(new byte[] {0x55}));

            assertThat(channel.isOpen()).isTrue();
            assertThat(dropped("unknown-opcode")).isEqualTo(1.0);
        }

        @Test
        void decodeError_shouldClose_whenTheFrameIsTooShortForItsPacket() {
            drop(channel.readOutbound());

            channel.writeInbound(clientFrame(new byte[] {0x07, 1}));

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.decode.errors")
                            .tag("state", "CONNECTED")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }

        private double dropped(String reason) {
            return TestChannels.registry(channel)
                    .get("l2nx.network.dropped")
                    .tag("reason", reason)
                    .counter()
                    .count();
        }
    }

    @Nested
    class Executor {
        @Test
        void packet_shouldCloseWithoutCountingAFlood_whenTheExecutorIsClosed() {
            drop(channel.readOutbound());
            tasks.close();

            channel.writeInbound(clientFrame(authGameGuard()));

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isNull();
            assertThat(TestChannels.registry(channel)
                            .find("l2nx.network.closed")
                            .tag("reason", CloseReason.FLOOD.tag())
                            .counter()
                            .count())
                    .isZero();
        }
    }

    @Nested
    class AuthDone {
        @Test
        void readIdle_shouldBeTheReadTimeout_beforeAuth() {
            drop(channel.readOutbound());

            assertThat(readerIdle()).isEqualTo(NetworkProperties.defaults().readTimeout());
        }

        @Test
        void authDone_shouldSendLoginFailAndCloseASilentSessionWithIdle_afterTheLoginTimeout() {
            drop(channel.readOutbound());

            LoginClient.of(channel).authDone();
            channel.runPendingTasks();
            assertThat(readerIdle()).isEqualTo(LoginController.LOGIN_TIMEOUT);
            idle(LoginController.LOGIN_TIMEOUT.minusSeconds(1));
            assertThat(channel.isOpen()).isTrue();

            idle(Duration.ofSeconds(1));

            assertClosedWith(LoginFailReason.REASON_ACCESS_FAILED);
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.IDLE);
            final Collection<Counter> closed =
                    TestChannels.registry(channel).get("l2nx.network.closed").counters();
            assertThat(closed.stream().mapToDouble(Counter::count).sum()).isEqualTo(1.0);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.closed")
                            .tag("reason", CloseReason.IDLE.tag())
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }

        private Duration readerIdle() {
            return Duration.ofMillis(
                    ((IdleStateHandler) channel.pipeline().get(PipelineNames.IDLE)).getReaderIdleTimeInMillis());
        }

        private void idle(Duration time) {
            channel.advanceTimeBy(time.toNanos(), TimeUnit.NANOSECONDS);
            channel.runScheduledPendingTasks();
        }
    }

    @Nested
    class SendServerList {
        @Test
        void sendServerList_shouldSendNothing_andStopWaiting_whenTheConnectionClosesWhileWaiting() {
            drop(channel.readOutbound());
            final LoginClient client = LoginClient.of(channel);
            client.awaitCharacters(List.of(1));
            client.sendServerList();
            channel.runPendingTasks();
            assertThat(client.isServerListWaiting()).isTrue();

            channel.close();
            channel.advanceTimeBy(LoginClient.CHARACTERS_WAIT.toMillis() * 2, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            assertThat(client.isServerListWaiting()).isFalse();
            assertThat((Object) channel.readOutbound()).isNull();
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldReleaseTheAccount_whenTheClientDidNotJoinAGameServer() {
            drop(channel.readOutbound());
            LoginClient.of(channel).setAccountId(ACCOUNT_ID);

            channel.close();

            assertThat(released).containsExactly(LoginClient.of(channel));
        }

        @Test
        void close_shouldKeepTheAccount_whenTheClientJoinedAGameServer() {
            drop(channel.readOutbound());
            LoginClient.of(channel).setAccountId(ACCOUNT_ID);
            LoginClient.of(channel).setJoinedServerId(1);

            channel.close();

            assertThat(released).isEmpty();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void closeForShutdown_shouldSendLoginFailWithTheMaintenanceReason_thenClose() {
            drop(channel.readOutbound());

            LoginClient.of(channel).closeForShutdown();

            assertClosedWith(LoginFailReason.REASON_SERVER_MAINTENANCE);
        }

        @Test
        void closeForExpiry_shouldSendLoginFailWithTheAccessFailedReason_thenClose() {
            drop(channel.readOutbound());

            LoginClient.of(channel).closeForExpiry();

            assertClosedWith(LoginFailReason.REASON_ACCESS_FAILED);
        }
    }

    private void assertClosedWith(LoginFailReason reason) {
        channel.runPendingTasks();
        final ByteBuf frame = channel.readOutbound();
        final byte[] payload = new byte[frame.readableBytes() - 2];
        frame.getBytes(frame.readerIndex() + 2, payload);
        frame.release();
        new LittleEndianBlowfishCipher(SESSION_KEY).decrypt(Unpooled.wrappedBuffer(payload), 0, payload.length);
        assertThat(payload[0]).as("LoginFail").isEqualTo((byte) 0x01);
        assertThat(payload[1]).as("reason").isEqualTo((byte) reason.code());
        assertThat(channel.isOpen()).isFalse();
    }

    private static byte[] authGameGuard() {
        final byte[] payload = new byte[21];
        payload[0] = 0x07;
        payload[1] = (byte) SESSION_ID;
        payload[2] = (byte) (SESSION_ID >>> 8);
        payload[3] = (byte) (SESSION_ID >>> 16);
        payload[4] = (byte) (SESSION_ID >>> 24);
        return payload;
    }

    private static ByteBuf clientFrame(byte[] payload) {
        int length = payload.length + 4;
        length += (8 - (length % 8)) % 8;
        final byte[] wire = Arrays.copyOf(payload, length);
        XorChecksum.append(Unpooled.wrappedBuffer(wire), 0, wire.length);
        new LittleEndianBlowfishCipher(SESSION_KEY).encrypt(Unpooled.wrappedBuffer(wire), 0, wire.length);
        final ByteBuf frame = Unpooled.buffer(2 + wire.length);
        frame.writeShortLE(2 + wire.length).writeBytes(wire);
        return frame;
    }

    private static void drop(Object message) {
        ReferenceCountUtil.release(message);
    }
}
