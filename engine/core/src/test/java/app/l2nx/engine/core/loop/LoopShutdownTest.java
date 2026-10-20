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
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
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
            final FakeInput input = new FakeInput(1, "input", log);
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
                                    .kind(TimerKind.ACTION)
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
