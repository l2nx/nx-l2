/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static app.l2nx.engine.core.loop.TestLoops.WALL_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.inbox.Wakeup;
import app.l2nx.engine.core.loop.timer.TimeBase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoopSchedulingTest {
    private final ManualGameClock clock = TestLoops.clock();
    private final Loop loop = TestLoops.loop(clock);
    private final List<String> ran = new ArrayList<>();

    private void passAfter(Duration step) {
        clock.advance(step);
        loop.runPass(clock.gameMillis());
    }

    private ScheduledFuture<Void> scheduleOffLoop(Runnable task, long delayMillis) throws Exception {
        return CompletableFuture.supplyAsync(() -> loop.schedule(task, delayMillis, TimeUnit.MILLISECONDS))
                .get();
    }

    private ScheduledFuture<Void> scheduleInPass(Runnable task, long delayMillis) {
        final AtomicReference<ScheduledFuture<Void>> future = new AtomicReference<>();
        loop.execute(() -> future.set(loop.schedule(task, delayMillis, TimeUnit.MILLISECONDS)));
        passAfter(Duration.ofMillis(1));
        return future.get();
    }

    @Nested
    class Schedule {
        @Test
        void schedule_shouldRunOnceAtDelay_whenCalledInPass() {
            loop.execute(() -> loop.schedule(() -> ran.add("a"), 100, TimeUnit.MILLISECONDS));
            passAfter(Duration.ofMillis(5));
            passAfter(Duration.ofMillis(99));
            assertThat(ran).isEmpty();

            passAfter(Duration.ofMillis(1));
            assertThat(ran).containsExactly("a");
        }

        @Test
        void schedule_shouldCountFromApplyingPass_whenCalledOffLoop() throws Exception {
            final ScheduledFuture<Void> future = scheduleOffLoop(() -> ran.add("a"), 50);
            passAfter(Duration.ofMillis(10));
            passAfter(Duration.ofMillis(49));
            assertThat(ran).isEmpty();

            passAfter(Duration.ofMillis(1));
            assertThat(ran).containsExactly("a");
            assertThat(future.isDone()).isTrue();
        }

        @Test
        void schedule_shouldReject_whenLoopStopped() {
            loop.shutdown();

            assertThatThrownBy(() -> loop.schedule(() -> {}, 1, TimeUnit.SECONDS))
                    .isInstanceOf(RejectedExecutionException.class);
        }

        @Test
        void schedule_shouldReject_whenLoopStoppedAndCalledOnItsThread() {
            loop.bind(Thread.currentThread(), Wakeup.NONE);
            loop.shutdown();

            assertThatThrownBy(() -> loop.schedule(() -> {}, 1, TimeUnit.SECONDS))
                    .isInstanceOf(RejectedExecutionException.class);
        }
    }

    @Nested
    class FixedRate {
        @Test
        void scheduleAtFixedRate_shouldCarryRemainder_whenPassesAreLate() {
            loop.execute(() -> loop.scheduleAtFixedRate(() -> ran.add("t"), 100, 100, TimeUnit.MILLISECONDS));
            passAfter(Duration.ofMillis(1));
            passAfter(Duration.ofMillis(130));
            passAfter(Duration.ofMillis(75));

            assertThat(ran).containsExactly("t", "t");
        }

        @Test
        void scheduleAtFixedRate_shouldKeepRunning_whenTaskThrows() {
            final AtomicInteger runs = new AtomicInteger();
            loop.execute(() -> loop.scheduleAtFixedRate(
                    () -> {
                        runs.incrementAndGet();
                        throw new IllegalStateException("boom");
                    },
                    10,
                    10,
                    TimeUnit.MILLISECONDS));
            passAfter(Duration.ofMillis(1));
            passAfter(Duration.ofMillis(10));
            passAfter(Duration.ofMillis(10));

            assertThat(runs).hasValue(2);
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({"0, MILLISECONDS", "-5, MILLISECONDS", "500, MICROSECONDS"})
        void scheduleAtFixedRate_shouldThrow_whenPeriodBelowOneMillisecond(long period, TimeUnit unit) {
            assertThatThrownBy(() -> loop.scheduleAtFixedRate(() -> {}, 0, period, unit))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Cancel {
        @Test
        void cancel_shouldPreventRun_andReturnTrueOnce() {
            final ScheduledFuture<Void> future = scheduleInPass(() -> ran.add("a"), 10);

            assertThat(future.cancel(true)).isTrue();
            assertThat(future.cancel(false)).isFalse();
            passAfter(Duration.ofMillis(20));

            assertThat(ran).isEmpty();
            assertThat(future.isCancelled()).isTrue();
            assertThat(future.isDone()).isTrue();
        }

        @Test
        void cancel_shouldReturnFalse_whenTaskAlreadyRan() {
            final ScheduledFuture<Void> future = scheduleInPass(() -> ran.add("a"), 1);
            passAfter(Duration.ofMillis(1));

            assertThat(future.cancel(false)).isFalse();
            assertThat(future.isCancelled()).isFalse();
        }

        @Test
        void cancel_shouldRemoveTimerAtOnce_whenCalledOnLoop() {
            final AtomicLong armed = new AtomicLong();
            final AtomicLong left = new AtomicLong();
            loop.execute(() -> {
                final ScheduledFuture<Void> future = loop.schedule(() -> {}, 10, TimeUnit.SECONDS);
                armed.set(loop.timers(TimeBase.GAME).size());
                future.cancel(false);
                left.set(loop.timers(TimeBase.GAME).size());
            });
            passAfter(Duration.ofMillis(1));

            assertThat(armed).hasValue(1);
            assertThat(left).hasValue(0);
        }

        @Test
        void cancel_shouldRemoveTimerInNextPass_whenCalledOffLoop() throws Exception {
            final ScheduledFuture<Void> future = scheduleInPass(() -> {}, 10_000);

            assertThat(CompletableFuture.supplyAsync(() -> future.cancel(false)).get())
                    .isTrue();
            assertThat(loop.timers(TimeBase.GAME).size()).isEqualTo(1);
            passAfter(Duration.ofMillis(1));

            assertThat(loop.timers(TimeBase.GAME).size()).isZero();
        }

        @Test
        void cancel_shouldStopFixedRate_whenTaskCancelsItself() {
            final AtomicReference<ScheduledFuture<Void>> future = new AtomicReference<>();
            final AtomicReference<Boolean> cancelled = new AtomicReference<>();
            loop.execute(() -> future.set(loop.scheduleAtFixedRate(
                    () -> {
                        ran.add("t");
                        cancelled.set(future.get().cancel(false));
                    },
                    10,
                    10,
                    TimeUnit.MILLISECONDS)));
            passAfter(Duration.ofMillis(1));
            passAfter(Duration.ofMillis(10));
            passAfter(Duration.ofMillis(10));

            assertThat(ran).containsExactly("t");
            assertThat(cancelled).hasValue(true);
            assertThat(future.get().isCancelled()).isTrue();
            assertThat(loop.timers(TimeBase.GAME).size()).isZero();
        }

        @Test
        void cancel_shouldReturnFalse_whenOneShotCancelsItselfWhileRunning() {
            final AtomicReference<ScheduledFuture<Void>> future = new AtomicReference<>();
            final AtomicReference<Boolean> cancelled = new AtomicReference<>();
            loop.execute(() -> future.set(
                    loop.schedule(() -> cancelled.set(future.get().cancel(false)), 1, TimeUnit.MILLISECONDS)));
            passAfter(Duration.ofMillis(1));
            passAfter(Duration.ofMillis(1));

            assertThat(cancelled).hasValue(false);
            assertThat(future.get().isDone()).isTrue();
            assertThat(future.get().isCancelled()).isFalse();
        }
    }

    @Nested
    class GetDelay {
        @Test
        void getDelay_shouldCountFromPassTime_whenOnLoop() {
            final AtomicLong delay = new AtomicLong();
            loop.execute(() -> {
                final ScheduledFuture<Void> future = loop.schedule(() -> {}, 1, TimeUnit.SECONDS);
                delay.set(future.getDelay(TimeUnit.MILLISECONDS));
            });
            passAfter(Duration.ofMillis(1));

            assertThat(delay).hasValue(1000);
        }

        // The queue floors a deadline armed while timers fire to the next millisecond; the delay follows the entry.
        @Test
        void getDelay_shouldBeTheArmedDeadline_whenScheduledDueFromATimer() {
            final AtomicLong delay = new AtomicLong();
            scheduleInPass(
                    () -> {
                        final ScheduledFuture<Void> future = loop.schedule(() -> ran.add("a"), 0, TimeUnit.SECONDS);
                        delay.set(future.getDelay(TimeUnit.MILLISECONDS));
                    },
                    10);
            passAfter(Duration.ofMillis(10));
            assertThat(ran).isEmpty();

            passAfter(Duration.ofMillis(1));
            assertThat(delay).hasValue(1);
            assertThat(ran).containsExactly("a");
        }

        @Test
        void getDelay_shouldReturnWholeDelay_whenNotArmedYet() throws Exception {
            final ScheduledFuture<Void> future = scheduleOffLoop(() -> {}, 50);
            clock.advance(Duration.ofMillis(30));

            assertThat(future.getDelay(TimeUnit.MILLISECONDS)).isEqualTo(50);
        }

        @Test
        void getDelay_shouldCountFromLiveClock_whenOffLoop() throws Exception {
            final ScheduledFuture<Void> future = scheduleInPass(() -> {}, 100);
            clock.advance(Duration.ofMillis(30));

            final long actual = CompletableFuture.supplyAsync(() -> future.getDelay(TimeUnit.MILLISECONDS))
                    .get();

            assertThat(actual).isEqualTo(70);
        }
    }

    @Nested
    class CompareTo {
        @Test
        void compareTo_shouldOrderByDelay() {
            final ScheduledFuture<Void> sooner = scheduleInPass(() -> {}, 10);
            final ScheduledFuture<Void> later = scheduleInPass(() -> {}, 20);

            assertThat(sooner.compareTo(later)).isNegative();
            assertThat(later.compareTo(sooner)).isPositive();
            assertThat(sooner.compareTo(sooner)).isZero();
        }
    }

    @Nested
    class Get {
        @Test
        void get_shouldThrow_whenCalledOnLoopBeforeDone() {
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            loop.execute(() -> {
                try {
                    loop.schedule(() -> {}, 1, TimeUnit.SECONDS).get();
                } catch (Exception | Error e) {
                    failure.set(e);
                }
            });
            passAfter(Duration.ofMillis(1));

            assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void get_shouldReturn_whenCalledOnLoopAfterDone() {
            final ScheduledFuture<Void> future = scheduleInPass(() -> {}, 1);
            passAfter(Duration.ofMillis(1));
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final AtomicInteger returned = new AtomicInteger();
            loop.execute(() -> {
                try {
                    future.get();
                    returned.incrementAndGet();
                } catch (Exception | Error e) {
                    failure.set(e);
                }
            });
            passAfter(Duration.ofMillis(1));

            assertThat(failure.get()).isNull();
            assertThat(returned).hasValue(1);
        }

        @Test
        void get_shouldWaitForRun_whenCalledOffLoop() throws Exception {
            final ScheduledFuture<Void> future = scheduleInPass(() -> ran.add("a"), 10);
            try (ExecutorService waiter = Executors.newVirtualThreadPerTaskExecutor()) {
                final Future<Void> waiting = waiter.submit(() -> future.get());
                passAfter(Duration.ofMillis(10));

                assertThat(waiting.get(5, TimeUnit.SECONDS)).isNull();
            }
            assertThat(ran).containsExactly("a");
        }

        @Test
        void get_shouldThrowExecutionException_whenTaskFailed() {
            final IllegalStateException boom = new IllegalStateException("boom");
            final ScheduledFuture<Void> future = scheduleInPass(
                    () -> {
                        throw boom;
                    },
                    1);
            passAfter(Duration.ofMillis(1));

            assertThat(future.isDone()).isTrue();
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCause(boom);
        }

        @Test
        void get_shouldThrowCancellationException_whenCancelled() {
            final ScheduledFuture<Void> future = scheduleInPass(() -> {}, 10);
            future.cancel(false);

            assertThatThrownBy(future::get).isInstanceOf(CancellationException.class);
        }

        @Test
        void get_shouldTimeOut_whenNotDoneInTime() {
            final ScheduledFuture<Void> future = scheduleInPass(() -> {}, 10);

            assertThatThrownBy(() -> future.get(10, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        }
    }

    @Nested
    class Execute {
        @Test
        void execute_shouldRunInNextPass_notInCurrent() {
            loop.execute(() -> {
                ran.add("first");
                loop.execute(() -> ran.add("second"));
            });
            passAfter(Duration.ofMillis(5));
            assertThat(ran).containsExactly("first");

            passAfter(Duration.ofMillis(5));
            assertThat(ran).containsExactly("first", "second");
        }

        @Test
        void execute_shouldReject_whenLoopStopped() {
            loop.shutdown();

            assertThatThrownBy(() -> loop.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        }
    }

    @Nested
    class PassTime {
        @Test
        void gameNowAndWallNow_shouldStayAtPassStart_whenClockMovesDuringPass() {
            final List<Long> seen = new ArrayList<>();
            loop.execute(() -> {
                clock.advance(Duration.ofSeconds(5));
                seen.add(loop.gameNow());
                seen.add(loop.wallNow());
            });
            passAfter(Duration.ofMillis(1));

            assertThat(seen).containsExactly(T0 + 1, WALL_START.toEpochMilli() + 1);
            assertThat(loop.getPassWallTime()).isEqualTo(WALL_START.toEpochMilli() + 1);
        }

        @Test
        void wallNow_shouldReturnLoopStart_whenNoPassRanYet() {
            assertThat(loop.wallNow()).isEqualTo(WALL_START.toEpochMilli());
        }
    }

    @Nested
    class InLoop {
        @Test
        void inLoop_shouldBeTrueOnlyInsideAPass_whenLoopIsNotPinned() {
            final AtomicReference<Boolean> inside = new AtomicReference<>();
            loop.execute(() -> inside.set(loop.inLoop()));
            passAfter(Duration.ofMillis(1));

            assertThat(inside).hasValue(true);
            assertThat(loop.inLoop()).isFalse();
        }
    }
}
