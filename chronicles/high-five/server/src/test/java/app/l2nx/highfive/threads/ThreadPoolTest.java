/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.ThreadConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

// The pools are static and GameServerTest starts them under the same lock.
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class ThreadPoolTest {
    private static final String SCHEDULED = "l2nx-scheduled";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void shutdown() {
        ThreadPool.shutdown();
    }

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldNotThrow_whenInitWasNeverCalled() {
            assertThatCode(ThreadPool::shutdown).doesNotThrowAnyException();
        }

        @Test
        void shutdown_shouldRemoveExecutorMeters() {
            ThreadPool.init(registry);

            ThreadPool.shutdown();

            assertThat(registry.find("executor.pool.size")
                            .tag("name", SCHEDULED)
                            .gauge())
                    .isNull();
            assertThat(registry.getMeters())
                    .noneMatch(meter -> meter.getId().getName().startsWith("executor"));
        }
    }

    @Nested
    class Init {
        @Test
        void init_shouldBindNewPools_whenInitializedAgainAfterShutdown() {
            ThreadPool.init(registry);
            ThreadPool.shutdown();

            ThreadPool.init(registry);

            final Gauge actual =
                    registry.get("executor.pool.size").tag("name", SCHEDULED).gauge();
            assertThat(actual.value()).isEqualTo(ThreadConfig.SCHEDULED_THREAD_POOL_SIZE);
        }
    }
}
