/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.account.AccountInfo;
import app.l2nx.login.account.AccountRepository;
import app.l2nx.login.account.AccountService;
import app.l2nx.login.account.TestPasswordEncoders;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.GameServerRepository;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.protocol.c621.LoginRoutes;
import app.l2nx.login.protocol.c621.ServerListMapper;
import app.l2nx.login.protocol.c621.handler.AuthGameGuardHandler;
import app.l2nx.login.protocol.c621.handler.RequestAuthLoginHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerListHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerLoginHandler;
import app.l2nx.login.session.AddressBans;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.ScrambledKeyPair;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.XorChecksum;
import app.l2nx.network.exec.BlockingTaskExecutor;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A packet's answer is offered from its lane's virtual thread, so these tests run on the local transport instead of
 * {@code EmbeddedChannel}, which is not thread-safe.
 */
class LoginPipelineLocalTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("0190a6b2-0000-7000-8000-000000000001");
    private static final byte[] SESSION_KEY = HexFormat.of().parseHex("0102030405060708090a0b0c0d0e0f10");
    private static final int SESSION_ID = 0x0BADF00D;

    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final BlockingTaskExecutor tasks = new BlockingTaskExecutor("login-test", 2, new SimpleMeterRegistry());
    private final BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
    private final CountDownLatch released = new CountDownLatch(1);
    private final LocalAddress address = new LocalAddress(UUID.randomUUID().toString());
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NetworkMetrics metrics = new NetworkMetrics(registry);
    private final ThreadRecordingEncoder encoder = new ThreadRecordingEncoder();
    private Channel client;
    private LoginKeys keys;
    private LoginRoutes routes;
    private ServerListMapper serverLists;

    @BeforeEach
    void wire() {
        wire(TestLoginProperties.create(new File(".")), mock(AccountRepository.class));
    }

    private void wire(LoginProperties properties, AccountRepository accounts) {
        final GameServerTable gameServerTable = new GameServerTable(mock(GameServerRepository.class), properties);
        final LoginController controller = new LoginController(accounts, gameServerTable, mock(TaskScheduler.class));
        final AccountService accountService =
                new AccountService(accounts, new AddressBans(properties), encoder, properties);
        accountService.afterPropertiesSet();
        serverLists = new ServerListMapper(gameServerTable);
        routes = new LoginRoutes(
                new AuthGameGuardHandler(),
                new RequestAuthLoginHandler(controller, accountService, properties),
                new RequestServerLoginHandler(controller, properties),
                new RequestServerListHandler());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close().sync();
        }
        tasks.close();
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
    }

    @Nested
    class AuthGameGuard {
        @Test
        void authGameGuard_shouldAnswerGgAuth_andCloseShouldReleaseTheAccount() throws Exception {
            connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").hasSize(192);

            client.writeAndFlush(clientFrame(authGameGuard())).sync();

            final byte[] ggAuth = decrypt(received.poll(5, TimeUnit.SECONDS));
            assertThat(ggAuth[0]).isEqualTo((byte) 0x0b);

            client.close().sync();
            assertThat(released.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Nested
    class States {
        @Test
        void packet_shouldBeCountedAsWrongState_whenTheStateItRunsInDoesNotAcceptIt() throws Exception {
            connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").isNotNull();

            client.writeAndFlush(clientFrame(new byte[] {0x05, 0, 0, 0, 0, 0, 0, 0, 0}))
                    .sync();
            client.writeAndFlush(clientFrame(authGameGuard())).sync();

            // The lane is serial: once GGAuth is out, the server list request queued before it has been judged.
            assertThat(decrypt(received.poll(5, TimeUnit.SECONDS))[0])
                    .as("GGAuth")
                    .isEqualTo((byte) 0x0b);
            assertThat(wrongState()).isEqualTo(1.0);
            assertThat(client.isActive()).isTrue();
        }
    }

    @Nested
    class Authentication {
        private static final String LOGIN = "test1";
        private static final String PASSWORD = "test1";

        private final AccountRepository accounts = mock(AccountRepository.class);

        @TempDir
        private Path datapackRoot;

        @Test
        void requestAuthLogin_shouldBeDiscardedUncounted_whenPipelinedBehindAFailedOne() throws Exception {
            install(true);
            when(accounts.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account()));
            connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").isNotNull();
            client.writeAndFlush(clientFrame(authGameGuard())).sync();
            assertThat(decrypt(received.poll(5, TimeUnit.SECONDS))[0])
                    .as("GGAuth")
                    .isEqualTo((byte) 0x0b);

            client.writeAndFlush(Unpooled.wrappedBuffer(
                            clientFrame(requestAuthLogin(LOGIN, "wrongpass")),
                            clientFrame(requestAuthLogin(LOGIN, "wrongpass"))))
                    .sync();

            assertThat(decrypt(received.poll(5, TimeUnit.SECONDS)))
                    .startsWith(0x01, LoginFailReason.REASON_USER_OR_PASS_WRONG.code());
            assertClosedWithoutError();
            verify(accounts, after(500).times(1)).findAccountInfo(eq(LOGIN), any());
            assertThat(wrongState()).isZero();
        }

        @Test
        void requestAuthLogin_shouldSendLoginFail_andClose_whenThePasswordIsWrong() throws Exception {
            install(true);
            when(accounts.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account()));

            final byte[] answer = authenticate(LOGIN, "wrongpass");

            assertThat(answer).startsWith(0x01, LoginFailReason.REASON_USER_OR_PASS_WRONG.code());
            assertClosedWithoutError();
        }

        @Test
        void requestAuthLogin_shouldSendLoginFail_andClose_whenTheAccountIsUnknown() throws Exception {
            install(false);

            final byte[] answer = authenticate("nobody", PASSWORD);

            assertThat(answer).startsWith(0x01, LoginFailReason.REASON_USER_OR_PASS_WRONG.code());
            assertClosedWithoutError();
        }

        @Test
        void requestAuthLogin_shouldRun_whenReadInTheSameFlushRightBehindTheAuthGameGuard() throws Exception {
            install(true);
            when(accounts.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account()));
            connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").isNotNull();

            client.writeAndFlush(Unpooled.wrappedBuffer(
                            clientFrame(authGameGuard()), clientFrame(requestAuthLogin(LOGIN, PASSWORD))))
                    .sync();

            assertThat(decrypt(received.poll(5, TimeUnit.SECONDS))[0])
                    .as("GGAuth")
                    .isEqualTo((byte) 0x0b);
            assertThat(decrypt(received.poll(5, TimeUnit.SECONDS))[0])
                    .as("LoginOk")
                    .isEqualTo((byte) 0x03);
            assertThat(wrongState()).isZero();
        }

        @Test
        void requestAuthLogin_shouldSendLoginOk_andStayOpen_whenThePasswordIsRight() throws Exception {
            install(true);
            when(accounts.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account()));

            final byte[] answer = authenticate(LOGIN, PASSWORD);

            assertThat(answer[0]).isEqualTo((byte) 0x03);
            assertThat(client.isActive()).isTrue();
            assertThat(closedOnError()).isZero();
        }

        @Test
        void requestAuthLogin_shouldCheckThePasswordOnAVirtualThread_neverOnTheEventLoop() throws Exception {
            install(true);
            when(accounts.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account()));

            final byte[] answer = authenticate(LOGIN, PASSWORD);

            assertThat(answer[0]).as("LoginOk").isEqualTo((byte) 0x03);
            final Thread actual = encoder.checks.poll(5, TimeUnit.SECONDS);
            assertThat(actual).isNotNull().isNotInstanceOf(FastThreadLocalThread.class);
            assertThat(actual.isVirtual()).isTrue();
        }

        private void install(boolean autoCreateAccounts) {
            wire(
                    TestLoginProperties.create(datapackRoot.toFile()).toBuilder()
                            .autoCreateAccounts(autoCreateAccounts)
                            .build(),
                    accounts);
        }

        private AccountInfo account() {
            return AccountInfo.builder()
                    .id(ACCOUNT_ID)
                    .login(LOGIN)
                    .passwordHash(encoder.encode(PASSWORD))
                    .accessLevel(0)
                    .lastServer(1)
                    .build();
        }

        private byte[] authenticate(String login, String password) throws Exception {
            connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").isNotNull();
            client.writeAndFlush(clientFrame(authGameGuard())).sync();
            assertThat(decrypt(received.poll(5, TimeUnit.SECONDS))[0])
                    .as("GGAuth")
                    .isEqualTo((byte) 0x0b);

            client.writeAndFlush(clientFrame(requestAuthLogin(login, password))).sync();
            return decrypt(received.poll(5, TimeUnit.SECONDS));
        }

        private byte[] requestAuthLogin(String login, String password) throws Exception {
            final byte[] payload = new byte[1 + 128 + 4 + 16];
            payload[0] = 0x00;
            System.arraycopy(credentials(login, password), 0, payload, 1, 128);
            return payload;
        }

        /** The 128-byte RSA block of the High Five client: login at 0x5E, password at 0x6C. */
        private byte[] credentials(String login, String password) throws Exception {
            final byte[] block = new byte[128];
            final byte[] user = login.getBytes(StandardCharsets.US_ASCII);
            final byte[] pass = password.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(user, 0, block, 0x5E, user.length);
            System.arraycopy(pass, 0, block, 0x6C, pass.length);
            final Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
            rsa.init(Cipher.ENCRYPT_MODE, keys.rsa().getPublicKey());
            return rsa.doFinal(block);
        }

        private void assertClosedWithoutError() {
            assertThat(client.closeFuture().awaitUninterruptibly(5, TimeUnit.SECONDS))
                    .as("closed")
                    .isTrue();
            assertThat(closedOnError()).isZero();
        }

        private double closedOnError() {
            return registry.get("l2nx.network.closed")
                    .tag("reason", CloseReason.ERROR.tag())
                    .counter()
                    .count();
        }
    }

    @Nested
    class SendServerList {
        @Test
        void sendServerList_shouldWaitForTheCounts_andSendThem_whenEveryServerAnswers() throws Exception {
            final LoginClient session = connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").isNotNull();
            session.awaitCharacters(List.of(1));
            session.sendServerList();
            assertThat(onEventLoop(session::isServerListWaiting)).isTrue();

            session.setCharsOnServ(1, 3);
            session.charactersArrived(1);

            assertThat(serverList()).startsWith(0x04, 0, 0, 0, 0, 1, 1, 3, 0);
        }

        @Test
        void sendServerList_shouldSendWithoutCounts_whenNoServerAnswersBeforeTheDeadline() throws Exception {
            final LoginClient session = connect();
            assertThat(received.poll(5, TimeUnit.SECONDS)).as("Init").isNotNull();
            final long start = System.nanoTime();
            session.awaitCharacters(List.of(1));
            session.sendServerList();

            final byte[] list = serverList();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(LoginClient.CHARACTERS_WAIT);
            assertThat(list).startsWith(0x04, 0, 0, 0, 0, 0);
            assertThat(onEventLoop(session::isServerListWaiting)).isFalse();
        }

        private byte[] serverList() throws InterruptedException {
            return decrypt(received.poll(5, TimeUnit.SECONDS));
        }
    }

    @Nested
    class EventLoopShutDown {
        @Test
        void charactersArrived_shouldNotThrow_whenTheEventLoopIsShutDown() throws Exception {
            final LoginClient session = connectAndShutDown();
            session.awaitCharacters(List.of(1));

            assertThatCode(() -> session.charactersArrived(1)).doesNotThrowAnyException();
        }

        @Test
        void sendServerList_shouldNotThrow_whenTheEventLoopIsShutDown() throws Exception {
            final LoginClient session = connectAndShutDown();

            assertThatCode(session::sendServerList).doesNotThrowAnyException();
        }

        private LoginClient connectAndShutDown() throws Exception {
            final LoginClient session = connect();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            // Its loop is gone with the group, so tearDown has nothing to close.
            client = null;
            return session;
        }
    }

    private LoginClient connect() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
        keys = new LoginKeys(new ScrambledKeyPair(generator.generateKeyPair()), SESSION_KEY);
        final BlockingQueue<LoginClient> sessions = new LinkedBlockingQueue<>();
        new ServerBootstrap()
                .group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.attr(NetworkMetrics.KEY).set(metrics);
                        new LoginChannelInitializer(
                                        LoginVariant.C621,
                                        NetworkProperties.defaults(),
                                        tasks,
                                        () -> keys,
                                        new FixedRandom(SESSION_ID),
                                        account -> released.countDown(),
                                        routes,
                                        serverLists)
                                .accept(ch);
                        sessions.add(LoginClient.of(ch));
                    }
                })
                .bind(address)
                .sync();
        client = new Bootstrap()
                .group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(Frames.decoder(4096), new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
                                received.add(ByteBufUtil.getBytes(frame));
                            }
                        });
                    }
                })
                .connect(address)
                .sync()
                .channel();
        return sessions.poll(5, TimeUnit.SECONDS);
    }

    private double wrongState() {
        final Counter counter = registry.find("l2nx.network.dropped")
                .tag("reason", DropReason.WRONG_STATE.tag())
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private <T> T onEventLoop(Callable<T> read) throws Exception {
        return group.submit(read).get(5, TimeUnit.SECONDS);
    }

    private static byte[] decrypt(byte[] frame) {
        assertThat(frame).as("frame").isNotNull();
        new LittleEndianBlowfishCipher(SESSION_KEY).decrypt(Unpooled.wrappedBuffer(frame), 0, frame.length);
        assertThat(XorChecksum.verify(Unpooled.wrappedBuffer(frame), 0, frame.length))
                .isTrue();
        return frame;
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

    private ByteBuf clientFrame(byte[] payload) {
        int length = payload.length + 4;
        length += (8 - (length % 8)) % 8;
        final byte[] wire = Arrays.copyOf(payload, length);
        XorChecksum.append(Unpooled.wrappedBuffer(wire), 0, wire.length);
        new LittleEndianBlowfishCipher(SESSION_KEY).encrypt(Unpooled.wrappedBuffer(wire), 0, wire.length);
        return client.alloc()
                .buffer(2 + wire.length)
                .writeShortLE(2 + wire.length)
                .writeBytes(wire);
    }

    private static final class ThreadRecordingEncoder implements PasswordEncoder {
        private final PasswordEncoder delegate = TestPasswordEncoders.argon2();
        private final BlockingQueue<Thread> checks = new LinkedBlockingQueue<>();

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            checks.add(Thread.currentThread());
            return delegate.matches(rawPassword, encodedPassword);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return delegate.upgradeEncoding(encodedPassword);
        }
    }
}
