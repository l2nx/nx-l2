/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopThreadTest {
    private static final ThreadFailureHandler UNEXPECTED = (thread, failure) -> {
        throw new AssertionError("Unexpected death of " + thread.getName(), failure);
    };

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SystemGameClock clock = new SystemGameClock(Clock.systemUTC());

    @Nested
    class Wake {
        @Test
        void offer_shouldWakeAParkedThreadAtMostOncePerPark() throws Exception {
            final LoopThread thread = new LoopThread("l2nx-loop-test-wake", registry, UNEXPECTED);
            final Loop loop = TestLoops.loop("wake", clock, registry);
            thread.add(loop);
            thread.start();
            try {
                awaitSleeping(thread);
                final CountDownLatch done = new CountDownLatch(3_000);
                try (ExecutorService producers = Executors.newFixedThreadPool(3)) {
                    for (int p = 0; p < 3; p++) {
                        producers.submit(() -> {
                            for (int i = 0; i < 1_000; i++) {
                                loop.offer(l -> done.countDown());
                            }
                        });
                    }
                }

                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                thread.requestStop();
                thread.join();
            }

            final double actual = registry.get("l2nx.engine.loop.wakeups")
                    .tag("thread", "l2nx-loop-test-wake")
                    .counter()
                    .count();
            assertThat(actual).isLessThanOrEqualTo(thread.getSleeps());
        }
    }

    @Nested
    class Add {
        @Test
        void add_shouldThrow_whenTheThreadWasAskedToStop() throws Exception {
            final LoopThread thread = new LoopThread("l2nx-loop-test-add", registry, UNEXPECTED);
            thread.start();
            thread.requestStop();
            thread.join();
            final Loop late = TestLoops.loop("late", clock, registry);

            assertThatIllegalStateException().isThrownBy(() -> thread.add(late));
            assertThat(late.offer(l -> {})).isTrue();
            late.random();
        }
    }

    @Nested
    class Park {
        @Test
        void thread_shouldParkUntilTheNextDeadline() throws Exception {
            final LoopThread thread = new LoopThread("l2nx-loop-test-deadline", registry, UNEXPECTED);
            final Loop loop = TestLoops.loop("deadline", clock, registry);
            final CountDownLatch fired = new CountDownLatch(1);
            thread.add(loop);
            thread.start();
            try {
                loop.offer(l -> l.timers(TimeBase.GAME)
                        .schedule(
                                l.getPassTime() + 20,
                                TimerSpec.builder()
                                        .handler((x, entry) -> fired.countDown())
                                        .kind(TimerKind.ACTION)
                                        .build()));

                assertThat(fired.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(loop.getPass()).isLessThanOrEqualTo(3);
            } finally {
                thread.requestStop();
                thread.join();
            }
        }

        // The manual clock stops one millisecond after the first pass, inside pass-min: only a timer pass that ignores
        // pass-min can run.
        @Test
        void thread_shouldRunADueTimerBeforePassMinElapses() throws Exception {
            final ManualGameClock manual = TestLoops.clock();
            final LoopThread thread = new LoopThread("l2nx-loop-test-pass-min", registry, UNEXPECTED);
            final Loop loop = TestLoops.loop("pass-min", manual, registry);
            final CountDownLatch armed = new CountDownLatch(1);
            final CountDownLatch fired = new CountDownLatch(1);
            thread.add(loop);
            thread.start();
            try {
                loop.offer(l -> {
                    l.timers(TimeBase.GAME)
                            .schedule(
                                    l.getPassTime() + 1,
                                    TimerSpec.builder()
                                            .handler((x, entry) -> fired.countDown())
                                            .kind(TimerKind.ACTION)
                                            .build());
                    armed.countDown();
                });
                assertThat(armed.await(2, TimeUnit.SECONDS)).isTrue();

                manual.advance(Duration.ofMillis(1));

                assertThat(fired.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(loop.getPass()).isEqualTo(2);
            } finally {
                thread.requestStop();
                thread.join();
            }
        }
    }

    @Nested
    class Interrupt {
        @Test
        void interrupt_shouldBeClearedBeforeTheNextPass_whenAPassLeavesItSet() throws Exception {
            final LoopThread thread = new LoopThread("l2nx-loop-test-interrupt", registry, UNEXPECTED);
            final Loop loop = TestLoops.loop("interrupt", clock, registry);
            final CountDownLatch interrupted = new CountDownLatch(1);
            final CompletableFuture<Boolean> stillInterrupted = new CompletableFuture<>();
            thread.add(loop);
            thread.start();
            try {
                loop.offer(l -> {
                    Thread.currentThread().interrupt();
                    interrupted.countDown();
                });
                assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();

                loop.offer(l -> stillInterrupted.complete(Thread.currentThread().isInterrupted()));

                assertThat(stillInterrupted.get(2, TimeUnit.SECONDS)).isFalse();
            } finally {
                thread.requestStop();
                thread.join();
            }
        }
    }

    @Nested
    class Start {
        @Test
        void thread_shouldBeADaemon_whenCreatedByANonDaemonThread() throws Exception {
            final CompletableFuture<LoopThread> created = new CompletableFuture<>();
            Thread.ofPlatform()
                    .daemon(false)
                    .start(() -> created.complete(new LoopThread("l2nx-loop-test-daemon", registry, UNEXPECTED)))
                    .join();

            assertThat(created.get(2, TimeUnit.SECONDS).getThread().isDaemon()).isTrue();
        }
    }

    @Nested
    class RequestStop {
        @Test
        void requestStop_shouldEndAnIdleThreadAndShutItsLoopsDown() throws Exception {
            final LoopThread thread = new LoopThread("l2nx-loop-test-stop", registry, UNEXPECTED);
            final Loop loop = TestLoops.loop("stop", clock, registry);
            final CountDownLatch hook = new CountDownLatch(1);
            loop.addShutdownHook(l -> hook.countDown());
            thread.add(loop);
            thread.start();
            awaitSleeping(thread);

            thread.requestStop();
            thread.join();

            assertThat(thread.getThread().isAlive()).isFalse();
            assertThat(hook.getCount()).isZero();
            assertThat(loop.offer(l -> {})).isFalse();
        }
    }

    @Nested
    class Death {
        @Test
        void errorInAPass_shouldStopTheLoopsAndReportTheFailure() throws Exception {
            final CompletableFuture<Throwable> reported = new CompletableFuture<>();
            final LoopThread thread =
                    new LoopThread("l2nx-loop-test-death", registry, (dead, failure) -> reported.complete(failure));
            final Loop loop = TestLoops.loop("death", clock, registry);
            final CountDownLatch hook = new CountDownLatch(1);
            loop.addShutdownHook(l -> hook.countDown());
            thread.add(loop);
            thread.start();

            loop.offer(l -> {
                throw new AssertionError("broken invariant");
            });
            thread.join();

            assertThat(reported.getNow(null)).isInstanceOf(AssertionError.class).hasMessage("broken invariant");
            assertThat(hook.getCount()).isZero();
            assertThat(loop.offer(l -> {})).isFalse();
        }
    }

    private static void awaitSleeping(LoopThread thread) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!thread.isSleeping()) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(1);
        }
    }
}
