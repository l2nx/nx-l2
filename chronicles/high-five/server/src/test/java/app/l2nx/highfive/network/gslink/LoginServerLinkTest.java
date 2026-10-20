/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.gslink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import app.l2nx.gslink.GsLinkProperties;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.ListingAttribute;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.command.AuthenticationResult;
import app.l2nx.highfive.login.command.KickAccount;
import app.l2nx.highfive.login.command.LinkLost;
import app.l2nx.highfive.login.command.PasswordChangeResult;
import app.l2nx.highfive.login.command.RegistrationResult;
import app.l2nx.highfive.login.command.ReportCharacters;
import app.l2nx.network.config.NetworkProperties;
import com.google.protobuf.ByteString;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// ServerConfig is static and read by other tests of the module.
@Isolated
class LoginServerLinkTest {
    private static final byte[] HEX_ID = {1, 2, 3};
    private static final UUID FIRST = UUID.fromString("0192f0a0-0000-7000-8000-000000000011");
    private static final UUID SECOND = UUID.fromString("0192f0a0-0000-7000-8000-000000000012");
    private static final UUID FAILING = UUID.fromString("0192f0a0-0000-7000-8000-000000000013");
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final FakeLoginServer loginServer = new FakeLoginServer();
    private final List<Object> commands = new CopyOnWriteArrayList<>();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private Server server;
    private ManagedChannel channel;
    private LoginServerLink link;
    // Set by a test to hold the link's thread inside the call's creation, before the stream exists.
    private volatile CountDownLatch connectEntered;
    private volatile CountDownLatch connectReleased;

    private int savedPort;
    private boolean savedAcceptAlternate;
    private List<String> savedSubnets;
    private List<String> savedHosts;
    private int savedMaxPlayers;
    private byte[] savedHexId;
    private int savedServerId;

    @BeforeEach
    void start() throws Exception {
        savedPort = ServerConfig.PORT_GAME;
        savedAcceptAlternate = ServerConfig.ACCEPT_ALTERNATE_ID;
        savedSubnets = ServerConfig.GAME_SERVER_SUBNETS;
        savedHosts = ServerConfig.GAME_SERVER_HOSTS;
        savedMaxPlayers = ServerConfig.MAXIMUM_ONLINE_USERS;
        savedHexId = ServerConfig.HEX_ID;
        savedServerId = ServerConfig.SERVER_ID;
        ServerConfig.PORT_GAME = 7777;
        ServerConfig.ACCEPT_ALTERNATE_ID = true;
        ServerConfig.GAME_SERVER_SUBNETS = List.of("0.0.0.0/0");
        ServerConfig.GAME_SERVER_HOSTS = List.of("127.0.0.1");
        ServerConfig.MAXIMUM_ONLINE_USERS = 100;
        ServerConfig.HEX_ID = HEX_ID;
        ServerConfig.SERVER_ID = 1;

        final String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(loginServer)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .intercept(new ClientInterceptor() {
                    @Override
                    public <Q, R> ClientCall<Q, R> interceptCall(
                            MethodDescriptor<Q, R> method, CallOptions options, Channel next) {
                        holdConnect();
                        return next.newCall(method, options);
                    }
                })
                .build();
        scheduler.initialize();
        link = new LoginServerLink(
                channel,
                GsLinkProperties.builder()
                        .secret("not-checked-by-the-fake-0123456789abcdef")
                        .reconnectDelay(GsLinkProperties.ReconnectDelay.builder()
                                .initial(Duration.ofMillis(50))
                                .max(Duration.ofMillis(200))
                                .build())
                        .build(),
                NetworkProperties.defaults().toBuilder()
                        .shutdownTimeout(SHUTDOWN_TIMEOUT)
                        .build(),
                scheduler,
                commands::add,
                commands::add,
                kick -> {
                    if (kick.accountId().equals(FAILING)) {
                        throw new IllegalStateException("The kick handler failed.");
                    }
                    commands.add(kick);
                },
                commands::add,
                commands::add,
                commands::add);
    }

    @AfterEach
    void stop() throws Exception {
        link.shutdown();
        scheduler.shutdown();
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        ServerConfig.PORT_GAME = savedPort;
        ServerConfig.ACCEPT_ALTERNATE_ID = savedAcceptAlternate;
        ServerConfig.GAME_SERVER_SUBNETS = savedSubnets;
        ServerConfig.GAME_SERVER_HOSTS = savedHosts;
        ServerConfig.MAXIMUM_ONLINE_USERS = savedMaxPlayers;
        ServerConfig.HEX_ID = savedHexId;
        ServerConfig.SERVER_ID = savedServerId;
    }

    @Test
    void start_shouldRegisterFirst_withTheServerIniData() throws Exception {
        link.start();

        final GsLinkProto.GameServerMessage actual =
                loginServer.nextConnection().received().next();

        assertThat(actual.getRegister())
                .isEqualTo(GsLinkProto.Register.newBuilder()
                        .setServerId(1)
                        .setAcceptAlternativeId(true)
                        .setHexId(ByteString.copyFrom(HEX_ID))
                        .setChronicle(LoginServerLink.CHRONICLE)
                        .setPort(7777)
                        .setMaxPlayers(100)
                        .addAddresses(GsLinkProto.GameServerAddress.newBuilder()
                                .setSubnet("0.0.0.0/0")
                                .setAddress("127.0.0.1"))
                        .build());
    }

    @Test
    void onNext_shouldTurnEveryLoginMessageIntoItsCommand() throws Exception {
        link.start();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        connection.received().next();

        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setRegistrationResult(GsLinkProto.RegistrationResult.newBuilder()
                                .setServerId(1)
                                .setServerName("Bartz"))
                        .build());
        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setAuthenticationResult(GsLinkProto.AuthenticationResult.newBuilder()
                                .setLogin("player")
                                .setAccepted(true)
                                .setAccountId(FIRST.toString()))
                        .build());
        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setAuthenticationResult(
                                GsLinkProto.AuthenticationResult.newBuilder().setLogin("intruder"))
                        .build());
        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setKickAccount(GsLinkProto.KickAccount.newBuilder().setAccountId(FIRST.toString()))
                        .build());
        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setReportCharacters(
                                GsLinkProto.ReportCharacters.newBuilder().setAccountId(SECOND.toString()))
                        .build());
        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setPasswordChangeResult(GsLinkProto.PasswordChangeResult.newBuilder()
                                .setCharacterName("Hero")
                                .setMessage("You have successfully changed your password!"))
                        .build());

        awaitCommands(6);
        assertThat(commands)
                .containsExactly(
                        RegistrationResult.builder()
                                .serverId(1)
                                .serverName("Bartz")
                                .hexId("010203")
                                .build(),
                        AuthenticationResult.builder()
                                .login("player")
                                .accountId(FIRST)
                                .accepted(true)
                                .build(),
                        AuthenticationResult.builder()
                                .login("intruder")
                                .accepted(false)
                                .build(),
                        new KickAccount(FIRST),
                        new ReportCharacters(SECOND),
                        PasswordChangeResult.builder()
                                .characterName("Hero")
                                .message("You have successfully changed your password!")
                                .build());
    }

    @Test
    void onNext_shouldKeepTheStream_whenACommandHandlerFails() throws Exception {
        link.start();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        connection.received().next();

        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setKickAccount(GsLinkProto.KickAccount.newBuilder().setAccountId(FAILING.toString()))
                        .build());
        connection
                .responses()
                .send(LoginServerMessage.newBuilder()
                        .setKickAccount(GsLinkProto.KickAccount.newBuilder().setAccountId(FIRST.toString()))
                        .build());

        awaitCommands(1);
        assertThat(commands).containsExactly(new KickAccount(FIRST));
        assertThat(connection.received().isEnded()).isFalse();
        assertThat(loginServer.hasNoMoreConnections()).isTrue();
    }

    @Test
    void authenticate_shouldReportTheLinkDown_whenNoStreamIsOpen() {
        assertThat(link.authenticate("player", KEY)).isFalse();
        assertThat(link.changePassword(FIRST, "Hero", "old", "new")).isFalse();
    }

    @Test
    void authenticate_shouldReportTheLinkDown_whileTheStreamOpens_andLeaveRegisterFirst() throws Exception {
        connectEntered = new CountDownLatch(1);
        connectReleased = new CountDownLatch(1);
        final Thread starting = Thread.ofPlatform().start(link::start);
        assertThat(connectEntered.await(5, TimeUnit.SECONDS)).isTrue();

        final boolean actual = link.authenticate("player", KEY);

        connectReleased.countDown();
        starting.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(actual).isFalse();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        assertThat(connection.received().next().hasRegister()).isTrue();
        assertThat(link.authenticate("player", KEY)).isTrue();
        assertThat(connection.received().next().getAuthenticate().getLogin()).isEqualTo("player");
    }

    @Test
    void stream_shouldReportTheLinkLost_whenItEndsAfterAnAuthenticateWentOut() throws Exception {
        link.start();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        connection.received().next();
        assertThat(link.authenticate("player", KEY)).isTrue();
        connection.received().next();

        connection.responses().fail(Status.UNAVAILABLE.withDescription("The login server is shutting down."));

        awaitCommands(1);
        assertThat(commands).containsExactly(new LinkLost());
    }

    @Test
    void stream_shouldRegisterWithTheGrantedId_afterTheLoginServerEndsIt() throws Exception {
        link.start();
        final FakeLoginServer.Connection first = loginServer.nextConnection();
        first.received().next();
        first.responses()
                .send(LoginServerMessage.newBuilder()
                        .setRegistrationResult(
                                GsLinkProto.RegistrationResult.newBuilder().setServerId(5))
                        .build());
        awaitCommands(1);

        first.responses().fail(Status.UNAVAILABLE.withDescription("The login server is shutting down."));

        assertThat(loginServer.nextConnection().received().next().getRegister().getServerId())
                .isEqualTo(5);
    }

    @Test
    void stream_shouldBeReopenedAndRegisterAgain_whenTheLoginServerEndsIt() throws Exception {
        link.start();
        final FakeLoginServer.Connection first = loginServer.nextConnection();
        first.received().next();

        first.responses().fail(Status.UNAVAILABLE.withDescription("The login server is shutting down."));

        assertThat(loginServer.nextConnection().received().next().hasRegister()).isTrue();
    }

    @Test
    void shutdown_shouldSendTheLogoutsThenDown_thenEndTheStreamAndTheChannel() throws Exception {
        link.start();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        connection.received().next();
        link.accountLeft(FIRST);
        link.accountLeft(SECOND);

        link.shutdown();

        assertThat(connection.received().next().getAccountLeft().getAccountId()).isEqualTo(FIRST.toString());
        assertThat(connection.received().next().getAccountLeft().getAccountId()).isEqualTo(SECOND.toString());
        assertThat(connection.received().next().getPublishListing().getStatus())
                .isEqualTo(GsLinkProto.ListingStatus.LISTING_STATUS_DOWN);
        assertThat(connection.received().awaitEnd().getCode()).isEqualTo(Status.Code.OK);
        assertThat(channel.isTerminated()).isTrue();
        assertThat(loginServer.hasNoMoreConnections()).isTrue();
        assertThat(commands)
                .as("the link ends on its own shutdown, nothing is lost")
                .isEmpty();
    }

    @Test
    void shutdown_shouldStopWaiting_whenTheShutdownTimeoutIsSpent() throws Exception {
        loginServer.setEndsItsSide(false);
        link.start();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        connection.received().next();
        scheduler.setClock(spentAfterFirstRead(SHUTDOWN_TIMEOUT));

        assertTimeoutPreemptively(SHUTDOWN_TIMEOUT.dividedBy(2), link::shutdown);

        assertThat(connection.received().next().getPublishListing().getStatus())
                .isEqualTo(GsLinkProto.ListingStatus.LISTING_STATUS_DOWN);
        assertThat(channel.isShutdown()).isTrue();
    }

    @Test
    void publishListing_shouldSendOnlyTheGivenAttributes() throws Exception {
        link.start();
        final FakeLoginServer.Connection connection = loginServer.nextConnection();
        connection.received().next();

        link.publishListing(Map.of(ListingAttribute.MAX_PLAYERS, 250, ListingAttribute.SQUARE_BRACKET, 1));

        final GsLinkProto.PublishListing actual = connection.received().next().getPublishListing();
        assertThat(actual.getMaxPlayers()).isEqualTo(250);
        assertThat(actual.getSquareBracket()).isTrue();
        assertThat(actual.hasStatus()).isFalse();
        assertThat(actual.hasAge()).isFalse();
    }

    private void holdConnect() {
        final CountDownLatch entered = connectEntered;
        if (entered == null) {
            return;
        }
        connectEntered = null;
        entered.countDown();
        try {
            connectReleased.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A clock whose first reading starts the timeout and every later reading finds it spent. */
    private static Clock spentAfterFirstRead(Duration timeout) {
        final AtomicInteger reads = new AtomicInteger();
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return reads.getAndIncrement() == 0 ? Instant.EPOCH : Instant.EPOCH.plus(timeout);
            }
        };
    }

    private void awaitCommands(int count) throws InterruptedException {
        for (int i = 0; (i < 50) && (commands.size() < count); i++) {
            Thread.sleep(100);
        }
    }
}
