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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BlockingTaskExecutorTest {
    private static final Runnable NOT_ABANDONED = () -> {
        throw new AssertionError("task abandoned");
    };

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
        void timers_shouldCountThePermitWaitAsIdle_whenEveryPermitIsHeld() throws Exception {
            final Duration permitWait = Duration.ofMillis(200);
            final Timer idle;
            try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry)) {
                idle = registry.get("executor.idle").tag("name", "test").timer();
                final CountDownLatch gate = holdThePermit(executor);
                final CountDownLatch done = new CountDownLatch(1);
                executor.execute(done::countDown, NOT_ABANDONED);
                awaitQueuedLanes(executor, 1);

                Thread.sleep(permitWait);
                gate.countDown();
                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(idle.max(TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(permitWait.toNanos());
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

    @Nested
    class Execute {
        @Test
        void execute_shouldRunATask_onlyWithAPermit() throws Exception {
            try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry)) {
                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch gate = new CountDownLatch(1);
                final CountDownLatch second = new CountDownLatch(1);
                executor.execute(
                        () -> {
                            started.countDown();
                            await(gate);
                        },
                        NOT_ABANDONED);
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

                executor.execute(second::countDown, NOT_ABANDONED);
                awaitQueuedLanes(executor, 1);
                assertThat(second.getCount()).as("waits for the permit").isEqualTo(1);

                gate.countDown();
                assertThat(second.await(5, TimeUnit.SECONDS)).isTrue();
            }
        }

        @Test
        void execute_shouldRefuse_afterClose() {
            final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry);
            executor.close();

            assertThatThrownBy(() -> executor.execute(() -> {}, NOT_ABANDONED))
                    .isInstanceOf(RejectedExecutionException.class);
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldRunTheAbandonCallbackInsteadOfATaskWaitingForAPermit_whenItInterruptsTheWait()
                throws Exception {
            final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry);
            final CountDownLatch gate = holdThePermit(executor);
            final AtomicInteger ran = new AtomicInteger();
            final CountDownLatch abandoned = new CountDownLatch(1);
            final AtomicBoolean interruptedOnAbandon = new AtomicBoolean();
            executor.execute(ran::incrementAndGet, () -> {
                interruptedOnAbandon.set(Thread.currentThread().isInterrupted());
                abandoned.countDown();
            });
            awaitQueuedLanes(executor, 1);

            closeInterrupted(executor);
            final boolean actual = abandoned.await(5, TimeUnit.SECONDS);
            gate.countDown();
            awaitActiveLanes(executor, 0);

            assertThat(actual).as("the abandon callback ran").isTrue();
            assertThat(interruptedOnAbandon)
                    .as("the interrupt flag is restored")
                    .isTrue();
            assertThat(ran.get()).isZero();
        }

        @Test
        void close_shouldHandALaneWaitingForAPermitToItsRejectionHandler_whenItInterruptsTheWait() throws Exception {
            final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, registry);
            final CountDownLatch gate = holdThePermit(executor);
            final AtomicInteger ran = new AtomicInteger();
            final List<Runnable> rejected = new CopyOnWriteArrayList<>();
            final AtomicBoolean interruptedOnRejection = new AtomicBoolean();
            final Runnable waitingTask = ran::incrementAndGet;
            executor.newLane(1, task -> {
                        interruptedOnRejection.set(Thread.currentThread().isInterrupted());
                        rejected.add(task);
                    })
                    .execute(waitingTask);
            awaitQueuedLanes(executor, 1);

            closeInterrupted(executor);
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (rejected.isEmpty() && (System.nanoTime() < deadline)) {
                Thread.sleep(1);
            }
            gate.countDown();
            awaitActiveLanes(executor, 0);

            assertThat(rejected).containsExactly(waitingTask);
            assertThat(interruptedOnRejection)
                    .as("the interrupt flag is restored")
                    .isTrue();
            assertThat(ran.get()).isZero();
        }

        // The holder ignores the interrupt, so the permit stays taken while the close interrupts the waiting task.
        // An interrupted closer skips the termination wait and goes straight to shutdownNow.
        private static void closeInterrupted(BlockingTaskExecutor executor) throws InterruptedException {
            final Thread closer = Thread.ofPlatform().start(() -> {
                Thread.currentThread().interrupt();
                executor.close();
            });
            closer.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(closer.isAlive()).isFalse();
        }
    }

    private double gauge(String name) {
        return registry.get(name).tag("name", "test").gauge().value();
    }

    private static CountDownLatch holdThePermit(BlockingTaskExecutor executor) throws InterruptedException {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch gate = new CountDownLatch(1);
        executor.execute(
                () -> {
                    started.countDown();
                    awaitUninterruptibly(gate);
                },
                NOT_ABANDONED);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        return gate;
    }

    private static void awaitQueuedLanes(BlockingTaskExecutor executor, int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((executor.queuedLanes() != expected) && (System.nanoTime() < deadline)) {
            Thread.sleep(1);
        }
        assertThat(executor.queuedLanes()).isEqualTo(expected);
    }

    private static void awaitActiveLanes(BlockingTaskExecutor executor, int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((executor.activeLanes() != expected) && (System.nanoTime() < deadline)) {
            Thread.sleep(1);
        }
        assertThat(executor.activeLanes()).isEqualTo(expected);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
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
