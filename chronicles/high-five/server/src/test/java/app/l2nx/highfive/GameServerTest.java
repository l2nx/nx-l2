/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.boot.ServerExit;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
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
         * Without a Spring context the first manager bean the start loads is missing; that happens after the thread
         * pool is up.
         */
        @Test
        void start_shouldStopEngineThreads_whenStartFails() throws Exception {
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final GameServer server = new GameServer(new ServerExit(new GenericApplicationContext()), registry);

            assertThatThrownBy(server::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("before the Spring context created it");

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
}
