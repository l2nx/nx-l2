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
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.repository.GameServerRepository;
import app.l2nx.network.config.NetworkProperties;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class GsLinkServiceTest {
    private static final String SECRET = "gslink-service-test-secret-0123456789";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofMillis(300);
    private static final UUID ACCOUNT = UUID.fromString("0192f0a0-0000-7000-8000-000000000002");

    @TempDir
    Path datapackRoot;

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private GameServerTable table;
    private GsLinkService service;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void start() throws Exception {
        Files.createDirectories(datapackRoot.resolve("data"));
        Files.writeString(datapackRoot.resolve("data/servername.xml"), GameServerLinkTest.SERVER_NAMES);
        table = new GameServerTable(
                mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
        table.load();
        scheduler.initialize();
        service = new GsLinkService(
                table,
                mock(LoginController.class),
                scheduler,
                NetworkProperties.defaults().toBuilder()
                        .handshakeTimeout(HANDSHAKE_TIMEOUT)
                        .build(),
                TestLoginProperties.create(datapackRoot.toFile()));
        final String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(
                        service, new SharedSecretServerInterceptor(SECRET), new RemoteAddressInterceptor()))
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

    private static void await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; (i < 50) && !condition.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).as("condition within 5 s").isTrue();
    }

    private record Stream(
            StreamObserver<GameServerMessage> requests, RecordingStreamObserver<LoginServerMessage> responses) {}
}
