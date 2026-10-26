/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.loop.inbox.FakeInput;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopShutdownTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Loop loop = TestLoops.loop("test", TestLoops.clock(), registry);
    private final List<String> log = new ArrayList<>();

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldDrainRunHooksAndRejectLaterOffers() {
            final FakeInput input = new FakeInput("input", log);
            input.push(loop, 3);
            loop.offer(l -> {
                log.add("message");
                l.defer(() -> log.add("deferred"));
            });
            loop.timers(TimeBase.GAME)
                    .schedule(
                            T0,
                            TimerSpec.builder()
                                    .handler((l, entry) -> log.add("deadline"))
                                    .build());
            loop.addShutdownHook(l -> {
                log.add("save");
                l.offer(x -> log.add("save result"));
            });

            loop.shutdown();

            assertThat(log).containsExactly("message", "deferred", "save", "save result");
            assertThat(input.hasInput()).isTrue();
            assertThat(input.isSignalled()).isFalse();
            assertThat(loop.offer(l -> {})).isFalse();
            assertThat(loop.signal(input)).isFalse();
        }

        @Test
        void shutdown_shouldBindTheLoopAsCurrent_whenRunningHooks() {
            final List<Loop> actual = new ArrayList<>();
            loop.addShutdownHook(l -> actual.add(Loop.current()));

            loop.shutdown();

            assertThat(actual).containsExactly(loop);
            assertThat(Loop.current()).isNull();
        }

        @Test
        void shutdown_shouldRunDrainsAndHooksUninterrupted_whenAnInterruptIsLeftSet() {
            final List<String> actual = new ArrayList<>();
            loop.offer(l -> {
                actual.add("drained message " + Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
            });
            loop.addShutdownHook(l -> {
                actual.add("first hook " + Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
            });
            loop.addShutdownHook(l -> {
                actual.add("second hook " + Thread.currentThread().isInterrupted());
                l.offer(x -> actual.add("hook result " + Thread.currentThread().isInterrupted()));
                Thread.currentThread().interrupt();
            });
            // As a pass that died with an Error leaves it.
            Thread.currentThread().interrupt();

            try {
                loop.shutdown();
            } finally {
                Thread.interrupted();
            }

            assertThat(actual)
                    .containsExactly(
                            "drained message false", "first hook false", "second hook false", "hook result false");
        }

        @Test
        void shutdown_shouldRunEveryHook_whenOneFails() {
            loop.addShutdownHook(l -> {
                throw new IllegalStateException("boom");
            });
            loop.addShutdownHook(l -> log.add("second"));

            loop.shutdown();

            assertThat(log).containsExactly("second");
        }

        @Test
        void shutdown_shouldRunTheHooksAndTerminateTheInbox_whenADrainedMessageThrowsAnError() {
            loop.offer(l -> {
                throw new AssertionError("broken invariant");
            });
            loop.offer(l -> log.add("after the error"));
            loop.addShutdownHook(l -> log.add("save"));

            assertThatThrownBy(loop::shutdown)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("broken invariant");

            assertThat(log).containsExactly("save", "after the error");
            assertThat(loop.offer(l -> {})).isFalse();
        }

        @Test
        void shutdown_shouldRunEveryHookAndRethrow_whenOneThrowsAnError() {
            loop.addShutdownHook(l -> {
                throw new AssertionError("broken hook");
            });
            loop.addShutdownHook(l -> log.add("second"));

            assertThatThrownBy(loop::shutdown)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("broken hook");

            assertThat(log).containsExactly("second");
            assertThat(loop.offer(l -> {})).isFalse();
        }

        @Test
        void shutdown_shouldBoundTheDrainOfASelfRenewingMessage() {
            final int[] runs = {0};
            loop.offer(new Message() {
                @Override
                public void execute(Loop l) {
                    runs[0]++;
                    l.offer(this);
                }
            });
            loop.addShutdownHook(l -> log.add("save"));

            loop.shutdown();

            assertThat(log).containsExactly("save");
            assertThat(runs[0]).isLessThanOrEqualTo(3 * Loop.SHUTDOWN_DRAIN_PASSES);
            assertThat(loop.offer(l -> {})).isFalse();
        }

        @Test
        void shutdown_shouldCancelPendingTasksAndReleaseTheirWaiters() throws Exception {
            final ScheduledFuture<Void> armed = loop.schedule(() -> log.add("armed"), 1, TimeUnit.SECONDS);
            final ScheduledFuture<Void> fixedRate =
                    loop.scheduleAtFixedRate(() -> log.add("fixed rate"), 1, 1, TimeUnit.SECONDS);
            loop.runPass(T0);
            final ScheduledFuture<Void> applied = loop.schedule(() -> log.add("applied"), 1, TimeUnit.SECONDS);
            loop.addShutdownHook(l -> l.schedule(() -> log.add("from hook"), 1, TimeUnit.SECONDS));
            final ExecutorService waiter = Executors.newVirtualThreadPerTaskExecutor();
            try {
                final Future<Void> waiting = waiter.submit(() -> armed.get());

                loop.shutdown();

                assertThatThrownBy(() -> waiting.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(CancellationException.class);
            } finally {
                waiter.shutdownNow();
            }
            assertThat(List.of(armed, fixedRate, applied)).allSatisfy(future -> {
                assertThat(future.isCancelled()).isTrue();
                assertThatThrownBy(future::get).isInstanceOf(CancellationException.class);
            });
            assertThat(log).isEmpty();
        }

        @Test
        void shutdown_shouldCancelATask_whenTheDrainBoundLeavesItsArmMessage() {
            final List<ScheduledFuture<Void>> futures = new ArrayList<>();
            loop.offer(new Message() {
                @Override
                public void execute(Loop l) {
                    // Off the loop, so the task arrives as a message behind this one and the last is never drained.
                    futures.add(CompletableFuture.supplyAsync(() -> l.schedule(() -> {}, 1, TimeUnit.SECONDS))
                            .join());
                    l.offer(this);
                }
            });

            loop.shutdown();

            assertThat(futures)
                    .isNotEmpty()
                    .allSatisfy(future -> assertThat(future.isCancelled()).isTrue());
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldRemoveTheMetersOfTheLoop() {
            loop.close();

            assertThat(registry.find("l2nx.engine.pass").tag("loop", "test").timer())
                    .isNull();
            assertThat(registry.find("l2nx.engine.inbox.depth")
                            .tag("loop", "test")
                            .gauge())
                    .isNull();
        }
    }
}
