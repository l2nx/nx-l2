/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.boot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.boot.integration.BootSmokeTest;
import app.l2nx.login.LoginServer;
import app.l2nx.login.config.LoginProperties;
import java.net.ConnectException;
import java.net.Socket;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// One context for the whole class, closed after the last nested class: the server keeps static state, so neither
// concurrent tests nor a second context in this JVM are possible.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LoginApplicationTest extends BootSmokeTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
            try (Socket socket = connect(properties().client().port())) {
                assertThat(socket.isConnected()).isTrue();
            }
        }
    }

    // The last nested class: closing its context afterwards is the shutdown that stopsCleanly() checks; the surviving
    // process shows it does not call System.exit.
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
        @ValueSource(strings = {"taskScheduler", "l2nx-login-io", "l2nx-login-packets"})
        void prometheus_shouldExposeExecutorMetrics(String executor) throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body())
                    .as("executor %s", executor)
                    .containsPattern("executor_pool_size_threads\\{[^}]*name=\"" + executor + "\"");
        }
    }

    /**
     * Runs after the context is closed. A check inside a test method is not possible: the Spring test framework still uses
     * the context after each test method.
     */
    @AfterAll
    static void stopsCleanly() {
        assertThat(LoginServer.getInstance().isRunning()).isFalse();
        assertThatThrownBy(() -> connect(properties().client().port()).close())
                .as("client port")
                .isInstanceOf(ConnectException.class);
        assertThatThrownBy(() -> connect(properties().gameServer().port()).close())
                .as("game server port")
                .isInstanceOf(ConnectException.class);
    }

    private static LoginProperties properties() {
        return LoginServer.getInstance().getProperties();
    }
}
