/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.Socket;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import app.l2nx.commons.integration.BootSmokeTest;
import app.l2nx.commons.lifecycle.ServerExit;
import app.l2nx.commons.lifecycle.ServerExitCode;
import app.l2nx.highfive.GameServer;
import app.l2nx.highfive.Shutdown;
import app.l2nx.highfive.config.ServerConfig;

/** The engine keeps static state, so the nested classes share one context and run in a fixed order, the engine stop last. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties =
{
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=file:${l2nx.test.login-sql-dir}/*.sql,file:${l2nx.test.game-sql-dir}/*.sql"
})
class HighFiveApplicationTest extends BootSmokeTest
{
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@MockitoBean
	ServerExit serverExit;

	@Autowired
	GameServer gameServer;

	@Autowired
	ConfigurableApplicationContext context;

	@Nested
	@Order(1)
	class Start
	{
		@Test
		void readiness_shouldBeUp_whenStarted() throws Exception
		{
			final HttpResponse<String> actual = get("/actuator/health/readiness");

			assertThat(actual.statusCode()).isEqualTo(200);
			final JsonNode body = JSON.readTree(actual.body());
			assertThat(body.path("status").asString()).isEqualTo("UP");
			assertThat(body.path("components").path("db").path("status").asString()).isEqualTo("UP");
		}

		@Test
		void clientPort_shouldAcceptTcp_whenStarted() throws Exception
		{
			try (Socket socket = connect(ServerConfig.PORT_GAME))
			{
				assertThat(socket.isConnected()).isTrue();
			}
		}
	}

	@Nested
	@Order(2)
	class Management
	{
		@Test
		void health_shouldBeUp() throws Exception
		{
			final HttpResponse<String> actual = get("/actuator/health");

			assertThat(actual.statusCode()).isEqualTo(200);
			assertThat(JSON.readTree(actual.body()).path("status").asString()).isEqualTo("UP");
		}

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings =
		{
			"build",
			"git",
			"java",
			"os",
			"process"
		})
		void info_shouldExposeSection(String section) throws Exception
		{
			final HttpResponse<String> actual = get("/actuator/info");

			assertThat(actual.statusCode()).isEqualTo(200);
			assertThat(JSON.readTree(actual.body()).path(section).isObject()).as("info section %s", section).isTrue();
		}

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings =
		{
			"l2nx-scheduled",
			"l2nx-instant",
			"l2nx-game-io",
			"l2nx-game-packets"
		})
		void prometheus_shouldExposeExecutorMetrics(String executor) throws Exception
		{
			final HttpResponse<String> actual = get("/actuator/prometheus");

			assertThat(actual.statusCode()).isEqualTo(200);
			assertThat(actual.body()).as("executor %s", executor).containsPattern("executor_pool_size_threads\\{[^}]*name=\"" + executor + "\"");
		}

		@Test
		void prometheus_shouldExposePlayersOnline() throws Exception
		{
			final HttpResponse<String> actual = get("/actuator/prometheus");

			assertThat(actual.statusCode()).isEqualTo(200);
			assertThat(actual.body()).contains("l2nx_players_online");
		}
	}

	@Nested
	@Order(3)
	class Countdown
	{
		@Test
		void startShutdown_shouldExitWithShutdownCode_whenCountdownEnds()
		{
			Shutdown.getInstance().startShutdown(null, 0, false);

			verify(serverExit, timeout(10_000)).exit(ServerExitCode.SHUTDOWN);
		}
	}

	@Nested
	@Order(4)
	class Stop
	{
		/**
		 * Stops the lifecycle beans as a context close does; the cached test context itself must stay open.
		 */
		@Test
		void stop_shouldRunShutdownActionsWithoutExit()
		{
			context.stop();

			assertThat(gameServer.isRunning()).isFalse();
			await().atMost(Duration.ofSeconds(30)).until(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(thread -> thread.getName().startsWith("l2nx ScheduledThread")));
			verifyNoInteractions(serverExit);
		}
	}
}
