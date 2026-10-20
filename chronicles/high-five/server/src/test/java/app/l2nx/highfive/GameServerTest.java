/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.boot.ServerExit;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.support.GenericApplicationContext;

// The engine keeps static state (config, thread pools, singletons), so nothing here may run concurrently.
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(ClockBridgeExtension.class)
@ResourceLock(ClockBridgeExtension.LOCK)
class GameServerTest {
    private static final long THREAD_EXIT_TIMEOUT_MS = 10_000;

    @Nested
    class Start {
        /**
         * Most Mobius loaders log and skip a failed database read; the start fails at the first one that does not, after the thread pool is up.
         */
        @Test
        void start_shouldStopEngineThreads_whenDatabaseIsUnreachable() throws Exception {
            final DatabaseFactory databaseFactory = new DatabaseFactory(unreachableDataSource());
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final GameServer server =
                    new GameServer(databaseFactory, new ServerExit(new GenericApplicationContext()), registry);

            assertThatThrownBy(server::start)
                    .hasRootCauseInstanceOf(SQLException.class)
                    .hasRootCauseMessage("unreachable");

            assertThat(server.isRunning()).isFalse();
            assertThat(registry.find("executor.pool.size")
                            .tag("name", "l2nx-scheduled")
                            .gauge())
                    .as("thread pool started")
                    .isNotNull();
            final List<Thread> engineThreads = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().startsWith("l2nx ")
                            || thread.getName().equals("GameTimeTaskManager"))
                    .toList();
            for (Thread thread : engineThreads) {
                thread.join(THREAD_EXIT_TIMEOUT_MS);
            }
            assertThat(engineThreads).as("engine threads").noneMatch(Thread::isAlive);
        }
    }

    private static DataSource unreachableDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    throw new SQLException("unreachable");
                });
    }
}
