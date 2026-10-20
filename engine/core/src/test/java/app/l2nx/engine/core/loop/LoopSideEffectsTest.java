/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.periodic.TestSystem;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoopSideEffectsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Loop loop = TestLoops.loop("test", TestLoops.clock(), registry);
    private final List<String> log = new ArrayList<>();

    private double overflows() {
        return registry.get("l2nx.engine.trigger.depth.exceeded")
                .tag("loop", "test")
                .counter()
                .count();
    }

    @Nested
    class RunPass {
        @Test
        void runPass_shouldRunThePhasesInSpecOrder() {
            loop.timers(TimeBase.GAME)
                    .schedule(
                            T0 + 10,
                            TimerSpec.builder()
                                    .handler((l, entry) -> log.add("deadline"))
                                    .kind(TimerKind.ACTION)
                                    .build());
            loop.addSystem(new TestSystem("system", Duration.ofMillis(10), log));
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> log.add("side-effect hook"));
            loop.addHook(PassPhase.REPLICATION, (l, now) -> log.add("replication"));
            loop.addHook(PassPhase.FLUSH, (l, now) -> log.add("flush"));
            loop.offer(l -> {
                log.add("message");
                l.defer(() -> log.add("deferred"));
            });

            loop.runPass(T0 + 10);

            assertThat(log)
                    .containsExactly(
                            "message", "deadline", "system@10", "deferred", "side-effect hook", "replication", "flush");
        }
    }

    @Nested
    class Defer {
        @Test
        void defer_shouldStopAtTheTriggerDepthAndContinueInTheNextPass() {
            final AtomicInteger runs = new AtomicInteger();
            final AtomicInteger hookRuns = new AtomicInteger();
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> hookRuns.incrementAndGet());
            loop.offer(l -> l.defer(new Chain(l, 20, runs)));

            loop.runPass(T0);
            assertThat(runs).hasValue(8);
            assertThat(hookRuns).hasValue(1);
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 5);

            loop.runPass(T0 + 5);
            assertThat(runs).hasValue(16);

            loop.runPass(T0 + 10);
            assertThat(runs).hasValue(20);
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(Long.MAX_VALUE);
            assertThat(overflows()).isEqualTo(2.0);
        }

        @ParameterizedTest(name = "chain of {0}")
        @CsvSource({"8, 8, 0", "9, 8, 1"})
        void defer_shouldCountAnOverflowOnlyWhenWorkIsLeft(int chain, int expectedRuns, double expectedOverflows) {
            final AtomicInteger runs = new AtomicInteger();
            final AtomicInteger hookRuns = new AtomicInteger();
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> hookRuns.incrementAndGet());
            loop.offer(l -> l.defer(new Chain(l, chain, runs)));

            loop.runPass(T0);

            assertThat(runs).hasValue(expectedRuns);
            assertThat(hookRuns).hasValue(1);
            assertThat(overflows()).isEqualTo(expectedOverflows);
        }

        @Test
        void defer_shouldRunTheHooksOnceAndCarryTheirWork_whenTheyDeferAfterTheLastGeneration() {
            final AtomicInteger runs = new AtomicInteger();
            final boolean[] deferredOnce = {false};
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> {
                log.add("hook");
                if (!deferredOnce[0]) {
                    deferredOnce[0] = true;
                    l.defer(() -> log.add("hook work"));
                }
            });
            loop.offer(l -> l.defer(new Chain(l, 8, runs)));

            loop.runPass(T0);
            assertThat(runs).hasValue(8);
            assertThat(log).containsExactly("hook");
            assertThat(overflows()).isEqualTo(1.0);

            loop.runPass(T0 + 5);
            assertThat(log).containsExactly("hook", "hook work", "hook");
        }

        @Test
        void defer_shouldRunBeforeReplication_whenASideEffectHookDefers() {
            final boolean[] deferredOnce = {false};
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> {
                log.add("hook");
                if (!deferredOnce[0]) {
                    deferredOnce[0] = true;
                    l.defer(() -> log.add("hook work"));
                }
            });
            loop.addHook(PassPhase.REPLICATION, (l, now) -> log.add("replication"));

            loop.runPass(T0);

            assertThat(log).containsExactly("hook", "hook work", "hook", "replication");
        }
    }

    private static final class Chain implements Runnable {
        private final Loop loop;
        private final int remaining;
        private final AtomicInteger runs;

        private Chain(Loop loop, int remaining, AtomicInteger runs) {
            this.loop = loop;
            this.remaining = remaining;
            this.runs = runs;
        }

        @Override
        public void run() {
            runs.incrementAndGet();
            if (remaining > 1) {
                loop.defer(new Chain(loop, remaining - 1, runs));
            }
        }
    }
}
