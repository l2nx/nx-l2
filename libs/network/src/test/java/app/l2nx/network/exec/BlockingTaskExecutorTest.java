/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BlockingTaskExecutorTest {
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Nested
    class Metrics {
        @Test
        void gauges_shouldReportPermitsInUseAndWaitingLanes() throws Exception {
            try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry)) {
                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch gate = new CountDownLatch(1);
                executor.newLane(1, BlockingTaskExecutorTest::unexpected).execute(() -> {
                    started.countDown();
                    await(gate);
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                executor.newLane(1, BlockingTaskExecutorTest::unexpected).execute(() -> {});
                awaitQueuedLanes(executor, 1);

                assertThat(gauge("executor.active")).isEqualTo(1.0);
                assertThat(gauge("executor.queued")).isEqualTo(1.0);
                assertThat(gauge("executor.pool.max")).isEqualTo(1.0);
                gate.countDown();
            }
        }

        @Test
        void timers_shouldRecordEachLaneRun_underTheExecutorName() throws Exception {
            final Timer execution;
            final Timer idle;
            try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 2, registry)) {
                execution =
                        registry.get("executor.execution").tag("name", "test").timer();
                idle = registry.get("executor.idle").tag("name", "test").timer();
                final CountDownLatch done = new CountDownLatch(2);
                executor.newLane(1, BlockingTaskExecutorTest::unexpected).execute(done::countDown);
                executor.newLane(1, BlockingTaskExecutorTest::unexpected).execute(done::countDown);
                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            }

            // Read after close, which waits for the runs to be recorded and unregisters the timers.
            assertThat(execution.count()).isEqualTo(2);
            assertThat(idle.count()).isEqualTo(2);
        }

        @Test
        void close_shouldRemoveTheGaugesAndTheTimers() {
            final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry);

            executor.close();

            assertThat(registry.find("executor.active").gauge()).isNull();
            assertThat(registry.find("executor.queued").gauge()).isNull();
            assertThat(registry.find("executor.pool.max").gauge()).isNull();
            assertThat(registry.find("executor.execution").timer()).isNull();
            assertThat(registry.find("executor.idle").timer()).isNull();
        }

        @Test
        void close_shouldKeepTheMeters_untilTheRunningTasksFinish() throws Exception {
            final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry);
            final CountDownLatch started = new CountDownLatch(1);
            final CountDownLatch gate = new CountDownLatch(1);
            executor.newLane(1, BlockingTaskExecutorTest::unexpected).execute(() -> {
                started.countDown();
                await(gate);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            final Thread closing = Thread.ofVirtual().start(executor::close);
            awaitShutdown(executor);

            assertThat(registry.find("executor.execution").tag("name", "test").timer())
                    .isNotNull();
            assertThat(registry.find("executor.active").tag("name", "test").gauge())
                    .isNotNull();

            gate.countDown();
            closing.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(registry.find("executor.execution").timer()).isNull();
            assertThat(registry.find("executor.active").gauge()).isNull();
        }

        @Test
        void close_shouldKeepTheMetersOfAnotherExecutorWithTheSameName() {
            final Timer other = registry.timer("executor.execution", "name", "test", "pool", "other");

            new BlockingTaskExecutor("test", 1, registry).close();

            assertThat(registry.find("executor.execution").tag("name", "test").timers())
                    .containsExactly(other);
        }

        @Test
        void constructor_shouldRefuseANameAlreadyRegistered() {
            try (BlockingTaskExecutor first = new BlockingTaskExecutor("test", 1, registry)) {
                assertThatThrownBy(() -> new BlockingTaskExecutor("test", 1, registry))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @Test
        void close_shouldKeepTheTimersOfAnotherExecutor() {
            try (BlockingTaskExecutor other = new BlockingTaskExecutor("other", 1, registry)) {
                new BlockingTaskExecutor("test", 1, registry).close();

                assertThat(registry.find("executor.execution")
                                .tag("name", "other")
                                .timer())
                        .isNotNull();
                assertThat(registry.find("executor.idle").tag("name", "other").timer())
                        .isNotNull();
            }
        }
    }

    private double gauge(String name) {
        return registry.get(name).tag("name", "test").gauge().value();
    }

    private static void awaitQueuedLanes(BlockingTaskExecutor executor, int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((executor.queuedLanes() < expected) && (System.nanoTime() < deadline)) {
            Thread.sleep(1);
        }
        assertThat(executor.queuedLanes()).isEqualTo(expected);
    }

    private static void awaitShutdown(BlockingTaskExecutor executor) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!executor.isClosed() && (System.nanoTime() < deadline)) {
            Thread.sleep(1);
        }
        assertThat(executor.isClosed()).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void unexpected(Runnable task) {
        throw new AssertionError("task rejected");
    }
}
