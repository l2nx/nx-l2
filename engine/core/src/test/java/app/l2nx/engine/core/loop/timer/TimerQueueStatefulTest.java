/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;

/**
 * {@link TimerQueue} against {@link TimerQueueModel} over random action chains ({@link TimerQueueHarness}).
 */
class TimerQueueStatefulTest {
    @Group
    class RunDue {
        @Property(tries = 1_000)
        void runDue_shouldFireLikeTheModel_whenActionsAreRandom(
                @ForAll("chains") ActionChain<TimerQueueHarness> chain) {
            chain.run();
        }

        @Provide
        Arbitrary<ActionChain<TimerQueueHarness>> chains() {
            return ActionChain.startWith(TimerQueueHarness::new)
                    .withAction(new Schedule())
                    .withAction(new ScheduleFromHandler())
                    .withAction(new ScheduleAtFixedRate())
                    .withAction(new Cancel())
                    .withAction(new Advance())
                    .withAction(new WallSetback())
                    .withAction(3, Action.just(Transformer.mutate("runPass", TimerQueueHarness::runPass)))
                    .withMaxTransformations(200);
        }
    }

    private static final class Schedule implements Action.Independent<TimerQueueHarness> {
        @Override
        public Arbitrary<Transformer<TimerQueueHarness>> transformer() {
            return Combinators.combine(
                            Arbitraries.of(TimeBase.values()),
                            Arbitraries.longs().between(-30, 300))
                    .as((base, delay) -> Transformer.mutate(
                            "schedule(" + base + ", " + delay + ")",
                            harness -> harness.schedule(base, delay, 0, null)));
        }
    }

    private static final class ScheduleFromHandler implements Action.Independent<TimerQueueHarness> {
        @Override
        public Arbitrary<Transformer<TimerQueueHarness>> transformer() {
            return Combinators.combine(
                            Arbitraries.of(TimeBase.values()),
                            Arbitraries.longs().between(-30, 300),
                            Arbitraries.longs().between(-30, 30))
                    .as((base, delay, childDelay) -> Transformer.mutate(
                            "scheduleFromHandler(" + base + ", " + delay + ", child " + childDelay + ")",
                            harness -> harness.schedule(base, delay, 0, childDelay)));
        }
    }

    private static final class ScheduleAtFixedRate implements Action.Independent<TimerQueueHarness> {
        @Override
        public Arbitrary<Transformer<TimerQueueHarness>> transformer() {
            return Combinators.combine(
                            Arbitraries.of(TimeBase.values()),
                            Arbitraries.longs().between(0, 300),
                            Arbitraries.longs().between(1, 120))
                    .as((base, delay, period) -> Transformer.mutate(
                            "scheduleAtFixedRate(" + base + ", " + delay + ", " + period + ")",
                            harness -> harness.schedule(base, delay, period, null)));
        }
    }

    private static final class Cancel implements Action.Independent<TimerQueueHarness> {
        @Override
        public Arbitrary<Transformer<TimerQueueHarness>> transformer() {
            return Combinators.combine(
                            Arbitraries.of(TimeBase.values()),
                            Arbitraries.integers().between(0, 10_000))
                    .as((base, index) -> Transformer.mutate(
                            "cancel(" + base + ", " + index + ")", harness -> harness.cancel(base, index)));
        }
    }

    private static final class Advance implements Action.Independent<TimerQueueHarness> {
        @Override
        public Arbitrary<Transformer<TimerQueueHarness>> transformer() {
            return Combinators.combine(
                            Arbitraries.longs().between(0, 400),
                            Arbitraries.longs().between(0, 400))
                    .as((game, wall) -> Transformer.mutate(
                            "advance(" + game + ", " + wall + ")", harness -> harness.advance(game, wall)));
        }
    }

    private static final class WallSetback implements Action.Independent<TimerQueueHarness> {
        @Override
        public Arbitrary<Transformer<TimerQueueHarness>> transformer() {
            return Arbitraries.longs()
                    .between(1, 400)
                    .map(back -> Transformer.mutate("wallSetback(" + back + ")", harness -> harness.wallSetback(back)));
        }
    }
}
