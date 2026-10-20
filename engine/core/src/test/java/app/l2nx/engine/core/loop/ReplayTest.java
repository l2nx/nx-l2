/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.inbox.FakeInput;
import app.l2nx.engine.core.loop.replay.InMemoryPassRecorder;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerEntry;
import app.l2nx.engine.core.loop.timer.TimerHandler;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReplayTest {
    private final Map<String, List<String>> logs = new ConcurrentHashMap<>();

    private static Loop recordedLoop(InMemoryPassRecorder recorder) {
        return new Loop(
                "test", TestLoops.clock(), 1L, EngineProperties.defaults(), new SimpleMeterRegistry(), recorder);
    }

    private static InMemoryPassRecorder.RecordedItem item(int order, FakeInput input, String name) {
        return InMemoryPassRecorder.RecordedItem.builder()
                .order(order)
                .sourceId(input.id())
                .item(FakeInput.Item.builder().name(name).source(input).build())
                .build();
    }

    @Nested
    class Replay {
        @Test
        void replay_shouldReproduceTheLoop_whenRunFromTheRecord() {
            final ManualGameClock clock = TestLoops.clock();
            final InMemoryPassRecorder recorder = new InMemoryPassRecorder(100);
            final Loop original =
                    new Loop("original", clock, 42L, EngineProperties.defaults(), new SimpleMeterRegistry(), recorder);
            original.offer(new Roll("a", logs));
            original.offer(new Roll("b", logs));
            original.runPass(clock.gameMillis());
            clock.advance(Duration.ofMillis(7));
            original.offer(new Roll("c", logs));
            original.runPass(clock.gameMillis());
            for (int i = 0; i < 20; i++) {
                clock.advance(Duration.ofMillis(5));
                original.runPass(clock.gameMillis());
            }

            final InMemoryPassRecorder.LoopRecord record = recorder.snapshot();
            assertThat(record.seed()).isEqualTo(42L);
            assertThat(record.passes()).hasSize(22);

            final ManualGameClock replayClock =
                    new ManualGameClock(Instant.ofEpochMilli(record.startWallMillis()), ZoneOffset.UTC);
            final Loop replay = new Loop(
                    "replay",
                    replayClock,
                    record.seed(),
                    EngineProperties.defaults(),
                    new SimpleMeterRegistry(),
                    PassRecorder.NOOP);
            for (InMemoryPassRecorder.PassRecord pass : record.passes()) {
                replayClock.advance(Duration.ofMillis(pass.gameMillis() - replayClock.gameMillis()));
                replayClock.advanceWall(Duration.ofMillis(pass.wallMillis() - replayClock.wallMillis()));
                for (InMemoryPassRecorder.RecordedInput input : pass.inputs()) {
                    if (input instanceof InMemoryPassRecorder.RecordedMessage message) {
                        replay.offer(message.message());
                    }
                }
                replay.runPass(pass.gameMillis());
            }

            assertThat(logs.get("original")).hasSizeGreaterThanOrEqualTo(6);
            assertThat(logs.get("replay")).isEqualTo(logs.get("original"));
        }
    }

    @Nested
    class Snapshot {
        @Test
        void snapshot_shouldKeepMessagesAndDrainedItemsInProcessingOrder() {
            final InMemoryPassRecorder recorder = new InMemoryPassRecorder(10);
            final Loop loop = recordedLoop(recorder);
            final FakeInput input = new FakeInput(7, "s", new ArrayList<>());
            input.push(loop, 3);
            final Message message = l -> {};
            loop.offer(message);

            loop.runPass(T0);

            final InMemoryPassRecorder.PassRecord pass =
                    recorder.snapshot().passes().getFirst();
            assertThat(pass.pass()).isEqualTo(1);
            assertThat(pass.gameMillis()).isEqualTo(T0);
            assertThat(pass.wallMillis()).isEqualTo(TestLoops.WALL_START.toEpochMilli());
            assertThat(pass.inputs())
                    .containsExactly(
                            new InMemoryPassRecorder.RecordedMessage(0, message),
                            item(1, input, "s0"),
                            item(2, input, "s1"),
                            item(3, input, "s2"));
        }

        @Test
        void snapshot_shouldKeepAFailingItemInProcessingOrder() {
            final InMemoryPassRecorder recorder = new InMemoryPassRecorder(10);
            final Loop loop = recordedLoop(recorder);
            final FakeInput input = new FakeInput(7, "s", new ArrayList<>());
            input.fail("s1");
            input.push(loop, 3);

            loop.runPass(T0);

            assertThat(recorder.snapshot().passes().getFirst().inputs())
                    .containsExactly(item(0, input, "s0"), item(1, input, "s1"), item(2, input, "s2"));
        }

        @Test
        void snapshot_shouldLeaveThePassOpen_whenTakenMidPass() {
            final InMemoryPassRecorder recorder = new InMemoryPassRecorder(10);
            final Loop loop = recordedLoop(recorder);
            final List<InMemoryPassRecorder.LoopRecord> midPass = new ArrayList<>();
            final Message first = l -> midPass.add(recorder.snapshot());
            final Message second = l -> {};
            loop.offer(first);
            loop.offer(second);

            loop.runPass(T0);
            loop.runPass(T0 + 5);

            assertThat(midPass.getFirst().passes())
                    .extracting(InMemoryPassRecorder.PassRecord::pass, InMemoryPassRecorder.PassRecord::inputs)
                    .containsExactly(tuple(1L, List.of(new InMemoryPassRecorder.RecordedMessage(0, first))));
            assertThat(recorder.snapshot().passes())
                    .extracting(InMemoryPassRecorder.PassRecord::pass, InMemoryPassRecorder.PassRecord::inputs)
                    .containsExactly(
                            tuple(
                                    1L,
                                    List.of(
                                            new InMemoryPassRecorder.RecordedMessage(0, first),
                                            new InMemoryPassRecorder.RecordedMessage(1, second))),
                            tuple(2L, List.of()));
        }

        @Test
        void snapshot_shouldRetainOnlyTheLastPasses_whenOverCapacity() {
            final InMemoryPassRecorder recorder = new InMemoryPassRecorder(2);
            final Loop loop = recordedLoop(recorder);

            for (int i = 0; i < 5; i++) {
                loop.runPass(T0 + 5L * i);
            }

            assertThat(recorder.snapshot().passes())
                    .extracting(InMemoryPassRecorder.PassRecord::pass)
                    .containsExactly(4L, 5L);
        }
    }

    @Nested
    class Drain {
        @Test
        void snapshot_shouldMarkThePassesOfTheShutdownDrain() {
            final InMemoryPassRecorder recorder = new InMemoryPassRecorder(10);
            final Loop loop = recordedLoop(recorder);
            final Message first = l -> {};
            final Message last = l -> {};
            loop.offer(first);
            loop.runPass(T0);
            loop.offer(last);

            loop.shutdown();

            assertThat(recorder.snapshot().passes())
                    .extracting(
                            InMemoryPassRecorder.PassRecord::pass,
                            InMemoryPassRecorder.PassRecord::drain,
                            InMemoryPassRecorder.PassRecord::inputs)
                    .containsExactly(
                            tuple(1L, false, List.of(new InMemoryPassRecorder.RecordedMessage(0, first))),
                            tuple(2L, true, List.of(new InMemoryPassRecorder.RecordedMessage(0, last))));
        }
    }

    private static final class Roll implements Message {
        private final String tag;
        private final Map<String, List<String>> logs;

        private Roll(String tag, Map<String, List<String>> logs) {
            this.tag = tag;
            this.logs = logs;
        }

        @Override
        public void execute(Loop loop) {
            final int delay = loop.random().nextInt(1, 60);
            logs.computeIfAbsent(loop.getName(), key -> new ArrayList<>())
                    .add(tag + " rolled " + delay + " at " + (loop.getPassTime() - T0));
            loop.timers(TimeBase.GAME)
                    .schedule(
                            loop.getPassTime() + delay,
                            TimerSpec.builder()
                                    .handler(new Fire(tag, logs))
                                    .kind(TimerKind.ACTION)
                                    .build());
        }
    }

    private static final class Fire implements TimerHandler {
        private final String tag;
        private final Map<String, List<String>> logs;

        private Fire(String tag, Map<String, List<String>> logs) {
            this.tag = tag;
            this.logs = logs;
        }

        @Override
        public void onTimer(Loop loop, TimerEntry entry) {
            logs.computeIfAbsent(loop.getName(), key -> new ArrayList<>())
                    .add(tag + " fired at " + (entry.getDeadline() - T0) + " drew "
                            + loop.random().nextInt(100));
        }
    }
}
