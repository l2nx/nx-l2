/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;

class SharedSecretServerInterceptorTest {
    private static final String SECRET = "interceptor-test-secret-0123456789";

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void start() throws IOException {
        final String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(
                        EchoLoginServer.service(), new SharedSecretServerInterceptor(SECRET)))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).build();
    }

    @AfterEach
    void stop() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void connect_shouldReachTheService_whenTheSecretMatches() throws Exception {
        final RecordingStreamObserver<LoginServerMessage> responses = connect(SECRET);

        assertThat(responses.next().getRegistrationResult().getServerId()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @NullSource
    @ValueSource(strings = {"wrong-secret", "interceptor-test-secret-012345678", "interceptor-test-secret-01234567890"})
    void connect_shouldBeUnauthenticated_whenTheSecretIsMissingOrWrong(String secret) throws Exception {
        final RecordingStreamObserver<LoginServerMessage> responses = connect(secret);

        assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(responses.remaining()).isEmpty();
    }

    private RecordingStreamObserver<LoginServerMessage> connect(String secret) {
        GsLinkGrpc.GsLinkStub stub = GsLinkGrpc.newStub(channel);
        if (secret != null) {
            stub = stub.withInterceptors(new BearerTokenAuthenticationInterceptor(secret));
        }
        final RecordingStreamObserver<LoginServerMessage> responses = new RecordingStreamObserver<>();
        final StreamObserver<GameServerMessage> requests = stub.connect(responses);
        requests.onNext(EchoLoginServer.register(1));
        return responses;
    }
}
