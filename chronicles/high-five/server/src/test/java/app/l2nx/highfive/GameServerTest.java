/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.context.support.GenericApplicationContext;

import app.l2nx.commons.database.DatabaseFactory;
import app.l2nx.boot.ServerExit;

// The engine keeps static state (config, thread pools, singletons), so nothing here may run concurrently.
@Execution(ExecutionMode.SAME_THREAD)
class GameServerTest
{
	private static final long THREAD_EXIT_TIMEOUT_MS = 10_000;

	@Nested
	class Start
	{
		/**
		 * Most Mobius loaders log and skip a failed database read; the start fails at the first one that does not, after the thread pool is up.
		 */
		@Test
		void start_shouldShutDownThreadPool_whenDatabaseIsUnreachable() throws Exception
		{
			final DatabaseFactory databaseFactory = new DatabaseFactory(unreachableDataSource());
			final SimpleMeterRegistry registry = new SimpleMeterRegistry();
			final GameServer server = new GameServer(databaseFactory, new ServerExit(new GenericApplicationContext()), registry);

			assertThatThrownBy(server::start).hasRootCauseInstanceOf(SQLException.class).hasRootCauseMessage("unreachable");

			assertThat(server.isRunning()).isFalse();
			assertThat(registry.find("executor.pool.size").tag("name", "l2nx-scheduled").gauge()).as("thread pool started").isNotNull();
			final List<Thread> poolThreads = Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith("l2nx ")).toList();
			for (Thread thread : poolThreads)
			{
				thread.join(THREAD_EXIT_TIMEOUT_MS);
			}
			assertThat(poolThreads).as("thread pool threads").noneMatch(Thread::isAlive);
		}
	}

	private static DataSource unreachableDataSource()
	{
		return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]
		{
			DataSource.class
		}, (proxy, method, args) ->
		{
			throw new SQLException("unreachable");
		});
	}
}
