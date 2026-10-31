/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gslink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.gslink.SharedSecretServerInterceptor;
import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.AuthenticationResult;
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
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.LoginKeys;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.SessionKey;
import app.l2nx.network.config.NetworkProperties;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class GsLinkServiceTest {
    private static final String SECRET = "gslink-service-test-secret-0123456789";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofMillis(300);
    private static final UUID ACCOUNT = UUID.fromString("0192f0a0-0000-7000-8000-000000000002");

    @TempDir
    Path datapackRoot;

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private GameServerTable table;
    private LoginController sessions;
    private GsLinkService service;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void start() throws Exception {
        Files.createDirectories(datapackRoot.resolve("data"));
        Files.writeString(datapackRoot.resolve("data/servername.xml"), GameServerLinkTest.SERVER_NAMES);
        final GameServerRepository repository = mock(GameServerRepository.class);
        final LoginProperties properties = TestLoginProperties.create(datapackRoot.toFile());
        table = new GameServerTable(repository, properties);
        table.load();
        scheduler.initialize();
        sessions = new LoginController(mock(AccountRepository.class), table, scheduler);
        service = new GsLinkService(
                new GameServerRegistry(table, repository, properties),
                table,
                sessions,
                mock(AccountService.class),
                scheduler,
                NetworkProperties.defaults().toBuilder()
                        .handshakeTimeout(HANDSHAKE_TIMEOUT)
                        .build(),
                properties);
        final String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(service, new SharedSecretServerInterceptor(SECRET)))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).build();
    }

    @AfterEach
    void stop() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        scheduler.shutdown();
    }

    @Test
    void connect_shouldRegisterTheGameServer() throws Exception {
        final Stream stream = connect(SECRET);

        stream.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));

        assertThat(stream.responses().next().getRegistrationResult().getServerName())
                .isEqualTo("Bartz");
        assertThat(table.getRegisteredGameServerById(1).isAuthed()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @NullSource
    @ValueSource(strings = "wrong-secret")
    void connect_shouldBeUnauthenticated_whenTheSecretIsMissingOrWrong(String secret) throws Exception {
        final Stream stream = connect(secret);

        stream.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));

        assertThat(stream.responses().awaitEnd().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(table.getRegisteredGameServerById(1)).isNull();
    }

    @Test
    void connect_shouldEndTheStream_whenNoRegisterArrivesWithinTheHandshakeTimeout() throws Exception {
        final Stream stream = connect(SECRET);

        assertThat(stream.responses().awaitEnd().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    }

    @Test
    void connect_shouldRefuseTheSecondStream_andKeepTheLiveOne_whenTheSameHexIdRegistersAgain() throws Exception {
        final Stream first = connect(SECRET);
        first.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));
        first.responses().next();
        final GameServerLink live = table.getRegisteredGameServerById(1).getGameServerLink();

        final Stream second = connect(SECRET);
        second.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));

        assertThat(second.responses().awaitEnd().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        final GameServerInfo actual = table.getRegisteredGameServerById(1);
        assertThat(actual.getGameServerLink()).isSameAs(live);
        assertThat(actual.isAuthed()).isTrue();
        first.requests().onNext(GameServerLinkTest.accountsEntered(ACCOUNT));
        await(() -> live.hasAccountOnGameServer(ACCOUNT));
    }

    @Test
    void connect_shouldRegisterAgain_afterTheStreamDropped() throws Exception {
        final Stream first = connect(SECRET);
        first.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));
        first.responses().next();
        final GameServerInfo info = table.getRegisteredGameServerById(1);

        first.requests().onError(Status.CANCELLED.asRuntimeException());
        await(() -> !info.isAuthed());
        final Stream second = connect(SECRET);
        second.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));

        assertThat(second.responses().next().getRegistrationResult().getServerId())
                .isEqualTo(1);
    }

    @Test
    void connect_shouldApplyEveryMessageBeforeTheEnd_thenMarkTheServerDown() throws Exception {
        final Stream stream = connect(SECRET);
        stream.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));
        stream.responses().next();
        final GameServerLink link = table.getRegisteredGameServerById(1).getGameServerLink();

        stream.requests().onNext(GameServerLinkTest.accountsEntered(ACCOUNT));
        stream.requests().onNext(GameServerLinkTest.accountLeft(ACCOUNT));
        stream.requests().onCompleted();

        assertThat(stream.responses().awaitEnd().getCode()).isEqualTo(Status.Code.OK);
        assertThat(link.hasAccountOnGameServer(ACCOUNT)).isFalse();
        assertThat(table.getRegisteredGameServerById(1).isAuthed()).isFalse();
    }

    @Test
    void authenticate_shouldAcceptTheKey_onlyOnTheStreamOfTheServerItsPlayOkNamed() throws Exception {
        final Stream serverOne = connect(SECRET);
        serverOne.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));
        serverOne.responses().next();
        final Stream serverTwo = connect(SECRET);
        serverTwo.requests().onNext(GameServerLinkTest.register(2, new byte[] {0x55, 0x66}, false, "high-five"));
        serverTwo.responses().next();
        final SessionKey key = SessionKey.builder()
                .loginOk1(11)
                .loginOk2(12)
                .playOk1(21)
                .playOk2(22)
                .build();
        final LoginClient client = new LoginClient(
                new EmbeddedChannel(),
                LoginVariant.C621,
                LoginKeys.builder().blowfishKey(new byte[16]).build(),
                1,
                null,
                null);
        client.setAccount("hero");
        sessions.tryCheckinAccount(
                client,
                "203.0.113.7",
                AccountInfo.builder()
                        .id(ACCOUNT)
                        .login("hero")
                        .passwordHash("hash")
                        .accessLevel(0)
                        .lastServer(1)
                        .build());
        client.setSessionKey(key);
        client.setJoinedServerId(1);

        serverTwo.requests().onNext(authenticate(key));
        final AuthenticationResult refused = serverTwo.responses().next().getAuthenticationResult();
        serverOne.requests().onNext(authenticate(key));
        final AuthenticationResult accepted = serverOne.responses().next().getAuthenticationResult();

        assertThat(refused.getAccepted()).isFalse();
        assertThat(accepted.getAccepted()).isTrue();
        assertThat(accepted.getAccountId()).isEqualTo(ACCOUNT.toString());
    }

    @Test
    void closeAll_shouldEndEveryStreamAsUnavailable() throws Exception {
        final Stream stream = connect(SECRET);
        stream.requests().onNext(GameServerLinkTest.register(1, GameServerLinkTest.HEX_ID, false, "high-five"));
        stream.responses().next();

        service.closeAll();

        assertThat(stream.responses().awaitEnd().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(table.getRegisteredGameServerById(1).isAuthed()).isFalse();
    }

    private Stream connect(String secret) {
        GsLinkGrpc.GsLinkStub stub = GsLinkGrpc.newStub(channel);
        if (secret != null) {
            stub = stub.withInterceptors(new BearerTokenAuthenticationInterceptor(secret));
        }
        final RecordingStreamObserver<LoginServerMessage> responses = new RecordingStreamObserver<>();
        return new Stream(stub.connect(responses), responses);
    }

    private static GameServerMessage authenticate(SessionKey key) {
        return GameServerMessage.newBuilder()
                .setAuthenticate(GsLinkProto.Authenticate.newBuilder()
                        .setLogin("hero")
                        .setKey(GsLinkProto.SessionKey.newBuilder()
                                .setLoginOk1(key.loginOk1())
                                .setLoginOk2(key.loginOk2())
                                .setPlayOk1(key.playOk1())
                                .setPlayOk2(key.playOk2()))
                        .setRequestId("0192f0a0-0000-7000-8000-0000000000bb"))
                .build();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; (i < 50) && !condition.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).as("condition within 5 s").isTrue();
    }

    private record Stream(
            StreamObserver<GameServerMessage> requests, RecordingStreamObserver<LoginServerMessage> responses) {}
}
