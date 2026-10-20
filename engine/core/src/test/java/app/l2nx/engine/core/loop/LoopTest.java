/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.loop.inbox.Wakeup;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Loop loop = TestLoops.loop("test", TestLoops.clock(), registry);
    private final List<String> log = new ArrayList<>();

    @Nested
    class RunPass {
        @Test
        void runPass_shouldRunMessagesInOfferOrder() {
            loop.offer(l -> log.add("a"));
            loop.offer(l -> log.add("b"));

            loop.runPass(T0);

            assertThat(log).containsExactly("a", "b");
            assertThat(loop.getPass()).isEqualTo(1);
            assertThat(loop.getPassTime()).isEqualTo(T0);
        }

        @Test
        void runPass_shouldLeaveMessagesOfferedDuringThePassForTheNextPass() {
            loop.offer(l -> {
                log.add("first");
                l.offer(x -> log.add("second"));
            });

            loop.runPass(T0);
            assertThat(log).containsExactly("first");

            loop.runPass(T0 + 5);
            assertThat(log).containsExactly("first", "second");
        }

        @Test
        void runPass_shouldRunHooksInPhaseOrder() {
            loop.addHook(PassPhase.FLUSH, (l, now) -> log.add("flush"));
            loop.addHook(PassPhase.REPLICATION, (l, now) -> log.add("replication"));
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> log.add("side effects"));
            loop.offer(l -> log.add("inbox"));

            loop.runPass(T0);

            assertThat(log).containsExactly("inbox", "side effects", "replication", "flush");
        }

        @Test
        void runPass_shouldContinueAfterAFailingMessage() {
            loop.offer(l -> {
                throw new IllegalStateException("boom");
            });
            loop.offer(l -> log.add("after"));

            loop.runPass(T0);

            assertThat(log).containsExactly("after");
            assertThat(registry.get("l2nx.engine.failures")
                            .tags("loop", "test", "phase", "inbox")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }

        @Test
        void runPass_shouldRejectTimeGoingBack() {
            loop.runPass(T0 + 10);

            assertThatIllegalArgumentException().isThrownBy(() -> loop.runPass(T0 + 9));
        }

        @Test
        void runPass_shouldAssertTheOwnerThread() {
            loop.bind(new Thread(() -> {}, "other-owner"), Wakeup.NONE);

            assertThatThrownBy(() -> loop.runPass(T0))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("other-owner");
            assertThatThrownBy(loop::random).isInstanceOf(AssertionError.class);
        }

        @Test
        void runPass_shouldCountAPassOverBudgetWithItsPhases() {
            final MockClock mock = new MockClock();
            final SimpleMeterRegistry timed = new SimpleMeterRegistry(SimpleConfig.DEFAULT, mock);
            final Loop slow = TestLoops.loop("slow", TestLoops.clock(), timed);
            slow.offer(l -> mock.add(Duration.ofMillis(60)));

            slow.runPass(T0);

            assertThat(timed.get("l2nx.engine.pass.over.budget")
                            .tag("loop", "slow")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(timed.get("l2nx.engine.pass.phase")
                            .tags("loop", "slow", "phase", "inbox")
                            .timer()
                            .totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(60.0);
            assertThat(timed.get("l2nx.engine.pass").tag("loop", "slow").timer().totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(60.0);
        }
    }

    @Nested
    class AddHook {
        @Test
        void addHook_shouldAcceptOnlyTheLastThreePhases() {
            assertThatIllegalArgumentException().isThrownBy(() -> loop.addHook(PassPhase.INBOX, (l, now) -> {}));
            assertThatIllegalArgumentException().isThrownBy(() -> loop.addHook(PassPhase.DEADLINES, (l, now) -> {}));
        }
    }

    @Nested
    class NextPassAt {
        @Test
        void nextPassAt_shouldBatchWorkArrivingWithinPassMin() {
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(Long.MAX_VALUE);

            loop.offer(l -> {});
            assertThat(loop.nextPassAt(loop.getPassTime())).isLessThan(T0);

            loop.runPass(T0 + 100);
            loop.offer(l -> {});
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 105);
        }
    }

    @Nested
    class Current {
        @Test
        void current_shouldBeTheLoopRunningThePass() {
            final AtomicReference<Loop> actual = new AtomicReference<>();
            loop.offer(l -> actual.set(Loop.current()));

            loop.runPass(T0);

            assertThat(actual).hasValue(loop);
            assertThat(Loop.current()).isNull();
        }
    }

    @Nested
    class Random {
        @Test
        void random_shouldBeTheSeededGenerator() {
            final long expected = RandomGeneratorFactory.of(Loop.RANDOM_ALGORITHM)
                    .create(TestLoops.SEED)
                    .nextLong();

            assertThat(loop.random().nextLong()).isEqualTo(expected);
            assertThat(loop.getSeed()).isEqualTo(TestLoops.SEED);
        }
    }

    @Nested
    class Bind {
        @Test
        void bind_shouldPinOnlyOnce() {
            loop.bind(new Thread(() -> {}), Wakeup.NONE);

            assertThatThrownBy(() -> loop.bind(new Thread(() -> {}), Wakeup.NONE))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Breakdown {
        @Test
        void breakdown_shouldListEveryPhase() {
            final long[] nanos = new long[PassPhase.values().length];
            nanos[PassPhase.INBOX.ordinal()] = 60_000_000;
            nanos[PassPhase.FLUSH.ordinal()] = 1_500_000;

            assertThat(Loop.breakdown(nanos))
                    .isEqualTo("inbox 60.00 ms, deadlines 0.00 ms, systems 0.00 ms, side_effects 0.00 ms, "
                            + "replication 0.00 ms, flush 1.50 ms");
        }
    }
}
