/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.inbox.FakeInput;
import app.l2nx.engine.core.loop.inbox.FakeInput.Operation;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoopInputTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Loop loop = TestLoops.loop("test", TestLoops.clock(), registry);
    private final List<String> log = new ArrayList<>();

    private static List<String> items(String label, int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> label + i).toList();
    }

    @Nested
    class RunPass {
        @Test
        void runPass_shouldTakeAtMostPacketsPerPassFromEachSourceRoundRobin() {
            final FakeInput flooding = new FakeInput(1, "A", log);
            final FakeInput quiet = new FakeInput(2, "B", log);
            flooding.push(loop, 40);
            quiet.push(loop, 3);

            loop.runPass(T0);
            final List<String> first = new ArrayList<>(items("A", 0, 16));
            first.addAll(items("B", 0, 3));
            assertThat(log).containsExactlyElementsOf(first);

            loop.runPass(T0 + 5);
            assertThat(log.subList(19, log.size())).containsExactlyElementsOf(items("A", 16, 32));

            loop.runPass(T0 + 10);
            assertThat(log.subList(35, log.size())).containsExactlyElementsOf(items("A", 32, 40));
            assertThat(flooding.isSignalled()).isFalse();
            assertThat(quiet.isSignalled()).isFalse();
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(Long.MAX_VALUE);
            assertThat(registry.get("l2nx.engine.pass.items")
                            .tags("loop", "test", "kind", "input")
                            .summary()
                            .totalAmount())
                    .isEqualTo(43.0);
        }

        @Test
        void runPass_shouldKeepInput_whenItArrivesWhileTheSignalIsCleared() {
            final FakeInput source = new FakeInput(1, "A", log);
            source.push(loop, 1);
            source.onClear = () -> {
                source.onClear = () -> {};
                source.push(loop, 1);
            };

            loop.runPass(T0);
            assertThat(log).containsExactly("A0");
            assertThat(source.isSignalled()).isTrue();

            loop.runPass(T0 + 5);
            assertThat(log).containsExactly("A0", "A1");
        }
    }

    @Nested
    class FailingInput {
        private double inboxFailures() {
            return inboxFailures("test");
        }

        private double inboxFailures(String loopName) {
            return registry.get("l2nx.engine.failures")
                    .tags("loop", loopName, "phase", "inbox")
                    .counter()
                    .count();
        }

        @Test
        void runPass_shouldReportAFailingItemOnceAndNeverRetryIt() {
            final FakeInput source = new FakeInput(1, "A", log);
            source.fail("A1");
            source.push(loop, 3);

            loop.runPass(T0);
            loop.runPass(T0 + 5);
            loop.runPass(T0 + 10);

            assertThat(source.runs("A1")).isEqualTo(1);
            assertThat(inboxFailures()).isEqualTo(1.0);
        }

        @Test
        void runPass_shouldRunTheRestOfTheBatchInTheSamePass_whenAnItemFails() {
            final FakeInput source = new FakeInput(1, "A", log);
            final FakeInput other = new FakeInput(2, "B", log);
            source.fail("A1");
            source.push(loop, 3);
            other.push(loop, 1);

            loop.runPass(T0);

            assertThat(log).containsExactly("A0", "A2", "B0");
        }

        @Test
        void nextPassAt_shouldBeIdle_whenTheLastItemFailed() {
            final FakeInput source = new FakeInput(1, "A", log);
            source.fail("A1");
            source.push(loop, 2);

            loop.runPass(T0);

            assertThat(source.hasInput()).isFalse();
            assertThat(source.isSignalled()).isFalse();
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(Long.MAX_VALUE);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("brokenSources")
        void runPass_shouldFinishThePassAndDropTheSourceUntilItSignalsAgain_whenTheSourceThrows(
                String operation, Consumer<FakeInput> breakage) {
            final FakeInput broken = new FakeInput(1, "A", log);
            final FakeInput other = new FakeInput(2, "B", log);
            broken.push(loop, 2);
            other.push(loop, 1);
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> log.add("hook"));
            breakage.accept(broken);

            loop.runPass(T0);
            broken.broken.clear();

            assertThat(log).endsWith("B0", "hook");
            assertThat(inboxFailures()).isEqualTo(1.0);
            assertThat(broken.isSignalled()).isFalse();
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(Long.MAX_VALUE);

            broken.push(loop, 1);
            loop.runPass(T0 + 5);

            assertThat(log).contains("A2");
            assertThat(broken.hasInput()).isFalse();
        }

        static Stream<Arguments> brokenSources() {
            return Stream.of(
                    broken("id", source -> source.broken.add(Operation.ID)),
                    broken("poll", source -> source.broken.add(Operation.POLL)),
                    broken("hasInput", source -> source.broken.add(Operation.HAS_INPUT)),
                    broken(
                            "trySignal",
                            source -> source.onClear = () -> {
                                source.onClear = () -> {};
                                source.enqueue(1);
                                source.broken.add(Operation.TRY_SIGNAL);
                            }));
        }

        private static Arguments broken(String operation, Consumer<FakeInput> breakage) {
            return Arguments.of(operation, breakage);
        }

        @Test
        void runPass_shouldFinishThePass_whenClearingTheSignalThrows() {
            final FakeInput broken = new FakeInput(1, "A", log);
            final FakeInput other = new FakeInput(2, "B", log);
            broken.push(loop, 1);
            other.push(loop, 1);
            loop.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> log.add("hook"));
            broken.broken.add(Operation.CLEAR_SIGNAL);

            loop.runPass(T0);

            assertThat(log).containsExactly("A0", "B0", "hook");
            assertThat(inboxFailures()).isEqualTo(2.0);
        }

        @Test
        void runPass_shouldRunTheItemsAndKeepTheSource_whenRecordingAnItemThrows() {
            final Loop recorded = new Loop(
                    "recorded",
                    TestLoops.clock(),
                    TestLoops.SEED,
                    EngineProperties.defaults(),
                    registry,
                    new PassRecorder() {
                        @Override
                        public void inputItem(long pass, int order, long sourceId, Message item) {
                            throw new IllegalStateException("record full");
                        }
                    });
            final FakeInput source = new FakeInput(1, "A", log);
            source.push(recorded, 2);
            recorded.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> log.add("hook"));

            recorded.runPass(T0);
            source.push(recorded, 1);
            recorded.runPass(T0 + 5);

            final List<String> expected = List.of("A0", "A1", "hook", "A2", "hook");
            assertThat(log).isEqualTo(expected);
            assertThat(inboxFailures("recorded")).isEqualTo(3.0);
            assertThat(source.isSignalled()).isFalse();
            assertThat(recorded.nextPassAt(recorded.getPassTime())).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void runPass_shouldRunTheMessageAndCompleteThePass_whenRecordingAMessageThrows() {
            final Loop recorded = new Loop(
                    "recorded",
                    TestLoops.clock(),
                    TestLoops.SEED,
                    EngineProperties.defaults(),
                    registry,
                    new PassRecorder() {
                        @Override
                        public void message(long pass, int order, Message message) {
                            throw new IllegalStateException("record full");
                        }
                    });
            recorded.addHook(PassPhase.SIDE_EFFECTS, (l, now) -> log.add("hook"));
            recorded.offer(l -> log.add("message"));

            recorded.runPass(T0);

            final List<String> expected = List.of("message", "hook");
            assertThat(log).isEqualTo(expected);
            assertThat(inboxFailures("recorded")).isEqualTo(1.0);
        }
    }

    @Nested
    class Signal {
        @Test
        void signal_shouldScheduleAPassAgain_whenTheSourceWasDrainedEmpty() {
            final FakeInput source = new FakeInput(1, "A", log);
            source.push(loop, 1);
            loop.runPass(T0);

            source.push(loop, 1);
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 5);
            loop.runPass(T0 + 5);

            assertThat(log).containsExactly("A0", "A1");
        }
    }
}
