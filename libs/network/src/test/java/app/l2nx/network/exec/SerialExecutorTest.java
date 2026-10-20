/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.exec;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SerialExecutorTest {
    @Test
    void execute_shouldRunTasksOfOneLaneInOrder() throws Exception {
        try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 4, new SimpleMeterRegistry())) {
            final SerialExecutor lane = executor.newLane(1_000, SerialExecutorTest::unexpected);
            final List<Integer> order = new CopyOnWriteArrayList<>();
            final CountDownLatch done = new CountDownLatch(500);

            for (int i = 0; i < 500; i++) {
                final int value = i;
                assertThat(lane.execute(() -> {
                            order.add(value);
                            done.countDown();
                        }))
                        .isTrue();
            }

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(order).isSorted().hasSize(500);
        }
    }

    @Test
    void execute_shouldBoundConcurrencyAcrossLanes() throws Exception {
        try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 2, new SimpleMeterRegistry())) {
            final AtomicInteger running = new AtomicInteger();
            final AtomicInteger peak = new AtomicInteger();
            final CountDownLatch done = new CountDownLatch(8);
            for (int i = 0; i < 8; i++) {
                executor.newLane(1, SerialExecutorTest::unexpected).execute(() -> {
                    peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                    sleep(20);
                    running.decrementAndGet();
                    done.countDown();
                });
            }

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(peak.get()).isLessThanOrEqualTo(2);
        }
    }

    @Test
    void execute_shouldRefuse_whenLaneIsFull() throws Exception {
        try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, new SimpleMeterRegistry())) {
            final CountDownLatch started = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            final SerialExecutor lane = executor.newLane(2, SerialExecutorTest::unexpected);

            assertThat(lane.execute(() -> {
                        started.countDown();
                        await(release);
                    }))
                    .isTrue();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lane.execute(() -> {})).isTrue();
            assertThat(lane.execute(() -> {})).isTrue();
            assertThat(lane.execute(() -> {})).isFalse();
            assertThat(lane.isClosed()).isFalse();
            release.countDown();
        }
    }

    @Test
    void execute_shouldKeepLaneAlive_whenTaskThrows() throws Exception {
        try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, new SimpleMeterRegistry())) {
            final SerialExecutor lane = executor.newLane(10, SerialExecutorTest::unexpected);
            final CountDownLatch done = new CountDownLatch(1);

            lane.execute(() -> {
                throw new IllegalStateException("boom");
            });
            lane.execute(done::countDown);

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void execute_shouldRunLaterTasks_whenATaskThrowsAnError() throws Exception {
        try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, new SimpleMeterRegistry())) {
            final SerialExecutor lane = executor.newLane(10, SerialExecutorTest::unexpected);
            final CountDownLatch done = new CountDownLatch(1);

            lane.execute(() -> {
                throw new StackOverflowError("thrown by the test");
            });
            lane.execute(done::countDown);

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void execute_shouldRefuse_whenExecutorIsClosed() {
        final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, new SimpleMeterRegistry());
        final SerialExecutor lane = executor.newLane(10, SerialExecutorTest::unexpected);
        executor.close();

        assertThat(lane.execute(() -> {})).isFalse();
        assertThat(lane.isClosed()).isTrue();
    }

    @Test
    void execute_shouldHandEveryAcceptedTaskToTheRejectionHandler_whenTheLaneCannotStart() {
        final List<Runnable> rejected = new ArrayList<>();
        final SerialExecutor lane = new SerialExecutor(drain -> false, () -> false, 2, rejected::add);
        final Runnable first = () -> {};
        final Runnable second = () -> {};

        assertThat(lane.execute(first)).isTrue();
        assertThat(lane.execute(second)).isTrue();

        assertThat(rejected).containsExactly(first, second);
        assertThat(lane.execute(() -> {})).as("pending count returned to zero").isTrue();
    }

    @Test
    void execute_shouldLetAWaitingLaneRun_afterOneBatchOfABusyLane() throws Exception {
        try (BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, new SimpleMeterRegistry())) {
            final SerialExecutor busy = executor.newLane(1_000, SerialExecutorTest::unexpected);
            final SerialExecutor waiting = executor.newLane(1, SerialExecutorTest::unexpected);
            final List<String> order = new CopyOnWriteArrayList<>();
            final CountDownLatch started = new CountDownLatch(1);
            final CountDownLatch gate = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(101);
            busy.execute(() -> {
                started.countDown();
                await(gate);
                order.add("busy");
                done.countDown();
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 99; i++) {
                busy.execute(() -> {
                    order.add("busy");
                    done.countDown();
                });
            }
            waiting.execute(() -> {
                order.add("waiting");
                done.countDown();
            });
            awaitQueuedLanes(executor, 1);

            gate.countDown();

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(order.indexOf("waiting")).isEqualTo(SerialExecutor.DRAIN_BATCH);
        }
    }

    @Test
    void close_shouldRejectTheTasksOfALaneWaitingForAPermit() throws Exception {
        final BlockingTaskExecutor executor = new BlockingTaskExecutor("test", 1, new SimpleMeterRegistry());
        final List<Runnable> rejected = new CopyOnWriteArrayList<>();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch gate = new CountDownLatch(1);
        final AtomicInteger ran = new AtomicInteger();
        executor.newLane(1, SerialExecutorTest::unexpected).execute(() -> {
            started.countDown();
            await(gate);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        final Runnable waitingTask = ran::incrementAndGet;
        executor.newLane(1, rejected::add).execute(waitingTask);
        awaitQueuedLanes(executor, 1);

        final Thread closer = Thread.ofPlatform().start(executor::close);
        while (!executor.isClosed()) {
            Thread.onSpinWait();
        }
        gate.countDown();
        closer.join(10_000);

        assertThat(ran.get()).isZero();
        assertThat(rejected).containsExactly(waitingTask);
    }

    private static void awaitQueuedLanes(BlockingTaskExecutor executor, int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((executor.queuedLanes() < expected) && (System.nanoTime() < deadline)) {
            Thread.sleep(1);
        }
        assertThat(executor.queuedLanes()).isEqualTo(expected);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void unexpected(Runnable task) {
        throw new AssertionError("Task rejected: " + task);
    }
}
