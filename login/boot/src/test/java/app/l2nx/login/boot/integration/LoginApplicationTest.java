/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.boot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.boot.integration.BootSmokeTest;
import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.LoginServer;
import app.l2nx.login.config.LoginProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import java.net.ConnectException;
import java.net.Socket;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Port 0 for the GS-link: parallel sessions on one machine share 9014.
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"l2nx.gslink.secret=login-application-test-secret-0123456789", "spring.grpc.server.port=0"})
class LoginApplicationTest extends BootSmokeTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static volatile int gsLinkPort;
    private static volatile LoginServer loginServer;
    private static volatile LoginProperties properties;

    @BeforeAll
    static void captureServer(@Autowired LoginServer server, @Autowired LoginProperties loginProperties) {
        loginServer = server;
        properties = loginProperties;
    }

    @Autowired
    void gsLinkPort(@LocalGrpcServerPort int port) {
        gsLinkPort = port;
    }

    @Nested
    @Order(1)
    class Start {
        @Test
        void readiness_shouldBeUp_whenStarted() throws Exception {
            final HttpResponse<String> actual = get("/actuator/health/readiness");

            assertThat(actual.statusCode()).isEqualTo(200);
            final JsonNode body = JSON.readTree(actual.body());
            assertThat(body.path("status").asString()).isEqualTo("UP");
            assertThat(body.path("components").path("db").path("status").asString())
                    .isEqualTo("UP");
        }

        @Test
        void clientPort_shouldAcceptTcp_whenStarted() throws Exception {
            try (Socket socket = connect(properties.client().ports().getFirst().port())) {
                assertThat(socket.isConnected()).isTrue();
            }
        }

        @Test
        void clientPort_shouldSendInit_whenConnected() throws Exception {
            try (Socket socket = connect(properties.client().ports().getFirst().port())) {
                final int low = socket.getInputStream().read();
                final int high = socket.getInputStream().read();

                // Init is the first frame: 192 bytes after the 2-byte length that counts itself.
                assertThat(low | (high << 8)).isEqualTo(194);
            }
        }

        @Test
        void gsLinkPort_shouldServeHealthWithoutTheSecret_andRefuseTheGsLinkWithoutIt() throws Exception {
            final ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", gsLinkPort)
                    .usePlaintext()
                    .build();
            try {
                assertThat(HealthGrpc.newBlockingStub(channel)
                                .check(HealthCheckRequest.getDefaultInstance())
                                .getStatus())
                        .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);

                final RecordingStreamObserver<LoginServerMessage> responses = new RecordingStreamObserver<>();
                GsLinkGrpc.newStub(channel).connect(responses).onNext(GameServerMessage.getDefaultInstance());
                assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
            } finally {
                channel.shutdownNow();
            }
        }
    }

    // Last nested class: closing the context afterwards is the shutdown stopsCleanly() checks.
    @Nested
    @Order(2)
    @DirtiesContext
    class Management {
        @Test
        void health_shouldBeUp() throws Exception {
            final HttpResponse<String> actual = get("/actuator/health");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(actual.body()).path("status").asString()).isEqualTo("UP");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"build", "git", "java", "os", "process"})
        void info_shouldExposeSection(String section) throws Exception {
            final HttpResponse<String> actual = get("/actuator/info");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(actual.body()).path(section).isObject())
                    .as("info section %s", section)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"taskScheduler"})
        void prometheus_shouldExposeExecutorMetrics(String executor) throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body())
                    .as("executor %s", executor)
                    .containsPattern("executor_pool_size_threads\\{[^}]*name=\"" + executor + "\"");
        }

        // A virtual-thread executor has no pool: its gauges report the semaphore, its tasks are timed.
        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "executor_execution_seconds_count",
                    "executor_active_threads",
                    "executor_queued_tasks",
                    "executor_pool_max_threads"
                })
        void prometheus_shouldExposeBlockingTaskMeters(String meter) throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body()).containsPattern(meter + "\\{[^}]*name=\"l2nx-login-tasks\"");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"l2nx_network_connections", "l2nx_network_bytes_total", "l2nx_network_closed_total"})
        void prometheus_shouldExposeNetworkMeters(String meter) throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body()).contains(meter);
        }
    }

    /**
     * Runs after the context is closed; inside a test method the Spring test framework still uses the context.
     */
    @AfterAll
    static void stopsCleanly() {
        assertThat(loginServer.isRunning()).isFalse();
        assertThatThrownBy(() ->
                        connect(properties.client().ports().getFirst().port()).close())
                .as("client port")
                .isInstanceOf(ConnectException.class);
        assertThatThrownBy(() -> connect(gsLinkPort).close()).as("GS-link port").isInstanceOf(ConnectException.class);
    }
}
