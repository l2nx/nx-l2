/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.context.support.GenericApplicationContext;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import app.l2nx.boot.ServerExit;
import app.l2nx.login.config.LoginConfig;

// The server keeps static state (config, thread pools, singletons), so nothing here may run concurrently.
@Execution(ExecutionMode.SAME_THREAD)
class LoginServerTest
{
	private static final long THREAD_EXIT_TIMEOUT_MS = 10_000;

	@Nested
	class Start
	{
		@Test
		void start_shouldShutDownStartedServices_whenClientPortIsTaken() throws Exception
		{
			LoginConfig.load();
			final LoginServer server = new LoginServer(null, new ServerExit(new GenericApplicationContext()), new SimpleMeterRegistry());

			try (ServerSocket blocker = new ServerSocket(LoginConfig.PORT_LOGIN, 1, InetAddress.getByName(LoginConfig.LOGIN_BIND_ADDRESS)))
			{
				assertThatThrownBy(server::start).isInstanceOf(UncheckedIOException.class).hasMessageContaining(String.valueOf(LoginConfig.PORT_LOGIN));
			}

			assertThat(server.isRunning()).isFalse();
			awaitExit(server.getGameServerListener());
			assertThat(server.getGameServerListener().isAlive()).as("game server listener thread").isFalse();
			assertThatCode(() -> new ServerSocket(LoginConfig.GAME_SERVER_LOGIN_PORT, 1, InetAddress.getByName(LoginConfig.GAME_SERVER_LOGIN_HOST)).close()).as("game server port released").doesNotThrowAnyException();

			final List<Thread> poolThreads = Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith("l2nx ")).toList();
			for (Thread thread : poolThreads)
			{
				awaitExit(thread);
			}
			assertThat(poolThreads).as("thread pool threads").noneMatch(Thread::isAlive);
		}
	}

	private static void awaitExit(Thread thread) throws InterruptedException
	{
		thread.join(THREAD_EXIT_TIMEOUT_MS);
	}
}
