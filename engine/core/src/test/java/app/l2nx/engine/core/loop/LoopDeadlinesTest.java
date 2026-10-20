/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import app.l2nx.engine.core.loop.timer.RecordingTimerHandler;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import app.l2nx.engine.core.loop.timer.TimerWheel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopDeadlinesTest {
    private final ManualGameClock clock = TestLoops.clock();
    private final Loop loop = TestLoops.loop(clock);
    private final RecordingTimerHandler handler = new RecordingTimerHandler();

    @Nested
    class GameDeadline {
        @Test
        void gameDeadline_shouldWakeTheLoopAndRunInTheDeadlinesPhase() {
            loop.timers(TimeBase.GAME).schedule(T0 + 20, handler.spec(1, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 20);

            loop.runPass(T0 + 20);
            assertThat(handler.fired).containsExactly("1:ACTION@" + (T0 + 20));
        }
    }

    @Nested
    class WallDeadline {
        @Test
        void wallDeadline_shouldBeConvertedToGameTimeForTheWakeUp() {
            final long wallDeadline = clock.wallMillis() + 30;
            loop.timers(TimeBase.WALL).schedule(wallDeadline, handler.spec(2, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 30);

            clock.advance(Duration.ofMillis(30));
            loop.runPass(clock.gameMillis());
            assertThat(handler.fired).containsExactly("2:ACTION@" + wallDeadline);
        }

        @Test
        void wallClockSetBack_shouldNeitherFireEarlyNorStrandDueEntries() {
            final long wallStart = clock.wallMillis();
            final TimerWheel wall = loop.timers(TimeBase.WALL);
            wall.schedule(wallStart + 30, handler.spec(1, TimerKind.ACTION));
            loop.runPass(T0);

            clock.advanceWall(Duration.ofMinutes(-10));
            clock.advance(Duration.ofMillis(30));
            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 30 + 600_000);
            loop.runPass(T0 + 30);
            assertThat(handler.fired).isEmpty();

            final long setBackNow = clock.wallMillis();
            wall.schedule(setBackNow - 5, handler.spec(2, TimerKind.ACTION));
            clock.advance(Duration.ofMillis(1));
            loop.runPass(T0 + 31);
            assertThat(handler.fired).containsExactly("2:ACTION@" + (setBackNow - 5));

            clock.advanceWall(Duration.ofMinutes(10));
            loop.runPass(T0 + 31);
            assertThat(handler.fired).containsExactly("2:ACTION@" + (setBackNow - 5), "1:ACTION@" + (wallStart + 30));
        }

        @Test
        void wallClockSetBack_shouldWakeAtADeadlineScheduledBehindTheCursor() {
            loop.runPass(T0);
            clock.advanceWall(Duration.ofMinutes(-10));
            clock.advance(Duration.ofMillis(1));
            loop.runPass(T0 + 1);

            final long wallDeadline = clock.wallMillis() + 20;
            loop.timers(TimeBase.WALL).schedule(wallDeadline, handler.spec(1, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 21);
            clock.advance(Duration.ofMillis(20));
            loop.runPass(T0 + 21);
            assertThat(handler.fired).containsExactly("1:ACTION@" + wallDeadline);
        }

        @Test
        void wallClockSetBack_shouldNotDelayADeadlineScheduledInTheNextPassInbox() {
            loop.runPass(T0);
            clock.advanceWall(Duration.ofMinutes(-10));
            clock.advance(Duration.ofMillis(1));
            final long wallDeadline = clock.wallMillis() + 20;
            loop.offer(l -> l.timers(TimeBase.WALL).schedule(wallDeadline, handler.spec(1, TimerKind.ACTION)));

            loop.runPass(T0 + 1);
            clock.advance(Duration.ofMillis(20));
            loop.runPass(T0 + 21);

            assertThat(handler.fired).containsExactly("1:ACTION@" + wallDeadline);
        }
    }

    @Nested
    class PassMin {
        @Test
        void gameDeadline_shouldBeThePassTime_whenItFallsDueInsidePassMin() {
            loop.runPass(T0);
            loop.timers(TimeBase.GAME).schedule(T0 + 1, handler.spec(1, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 1);
        }

        @Test
        void wallDeadline_shouldBeThePassTime_whenItFallsDueInsidePassMin() {
            loop.runPass(T0);
            loop.timers(TimeBase.WALL).schedule(clock.wallMillis() + 2, handler.spec(1, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 2);
        }

        @Test
        void inputBurst_shouldRunAtMostOnePassPerPassMin() {
            loop.runPass(T0);
            final List<Long> passes = new ArrayList<>();

            for (long now = T0 + 1; now <= T0 + 20; now++) {
                clock.advance(Duration.ofMillis(1));
                for (int i = 0; i < 50; i++) {
                    loop.offer(l -> {});
                }
                if (loop.nextPassAt(now) <= now) {
                    loop.runPass(now);
                    passes.add(now - T0);
                }
            }

            assertThat(passes).containsExactly(5L, 10L, 15L, 20L);
        }

        @Test
        void dueDeadline_shouldTakeTheQueuedInputAlong_whenItFallsDueBeforeTheGate() {
            loop.runPass(T0);
            loop.offer(l -> handler.fired.add("input@" + l.getPassTime()));
            loop.timers(TimeBase.GAME).schedule(T0 + 2, handler.spec(1, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 2);
            loop.runPass(T0 + 2);

            assertThat(handler.fired).containsExactly("input@" + (T0 + 2), "1:ACTION@" + (T0 + 2));
            loop.offer(l -> {});
            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 7);
        }

        @Test
        void overdueRemainder_shouldRunOneMillisecondLaterWithoutWaitingForPassMin() {
            final EngineProperties settings =
                    EngineProperties.defaults().toBuilder().overduePerPass(2).build();
            final Loop capped =
                    new Loop("capped", clock, TestLoops.SEED, settings, new SimpleMeterRegistry(), PassRecorder.NOOP);
            for (int i = 1; i <= 3; i++) {
                capped.timers(TimeBase.GAME).schedule(T0 + i, handler.spec(i, TimerKind.ACTION));
            }

            capped.runPass(T0 + 100);
            assertThat(handler.fired).containsExactly("1:ACTION@" + (T0 + 1), "2:ACTION@" + (T0 + 2));
            assertThat(capped.nextPassAt(T0 + 100)).isEqualTo(T0 + 101);

            capped.runPass(T0 + 101);
            assertThat(handler.fired).endsWith("3:ACTION@" + (T0 + 3));
        }

        @Test
        void queuedInput_shouldWaitOnlyForTheGate_whenTheDeadlineFallsDueAfterIt() {
            loop.runPass(T0);
            loop.offer(l -> {});
            loop.timers(TimeBase.GAME).schedule(T0 + 8, handler.spec(1, TimerKind.ACTION));

            assertThat(loop.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 5);
        }
    }

    @Nested
    class PassRate {
        // A busy loop thread asks again right after each pass; the cap stands in for "forever" within one reading.
        private static final int CHECKS_PER_MILLISECOND = 1_000;

        @Test
        void rearmAtPassTime_shouldRunAtMostOnePassPerMillisecond() {
            final TimerSpec rearm = TimerSpec.builder()
                    .handler((l, entry) -> l.timers(TimeBase.GAME).schedule(l.getPassTime(), entry.getSpec()))
                    .kind(TimerKind.ACTION)
                    .build();
            loop.timers(TimeBase.GAME).schedule(T0, rearm);

            final int actual = simulate(loop, 100, () -> {});

            assertThat(actual).isEqualTo(100);
        }

        @Test
        void stuckOverdueBacklog_shouldRunAtMostOnePassPerMillisecond() {
            final EngineProperties settings =
                    EngineProperties.defaults().toBuilder().overduePerPass(1).build();
            final Loop capped =
                    new Loop("capped", clock, TestLoops.SEED, settings, new SimpleMeterRegistry(), PassRecorder.NOOP);
            final TimerSpec stuck = TimerSpec.builder()
                    .handler((l, entry) -> l.timers(TimeBase.GAME).schedule(entry.getDeadline(), entry.getSpec()))
                    .kind(TimerKind.ACTION)
                    .build();
            capped.timers(TimeBase.GAME).schedule(T0, stuck);
            capped.timers(TimeBase.GAME).schedule(T0 + 1, stuck);
            clock.advance(Duration.ofMillis(100));

            final int actual = simulate(capped, 100, () -> {});

            assertThat(actual).isEqualTo(100);
        }

        @Test
        void denseTimersAndInputFlood_shouldRunAtMostOnePassPerMillisecond() {
            for (int i = 0; i < 10; i++) {
                loop.timers(TimeBase.GAME).schedule(T0 + 1, handler.periodic(i, 1));
            }
            final int[] input = new int[1];

            final int actual = simulate(loop, 1_000, () -> {
                for (int i = 0; i < 50; i++) {
                    loop.offer(l -> input[0]++);
                }
            });

            assertThat(actual).isLessThanOrEqualTo(1_000);
            assertThat(handler.fired).hasSize(10 * 999);
            assertThat(input[0]).isEqualTo(50 * 1_000);
        }

        @Test
        void loneFarTimer_shouldFireWithinOneMillisecondOfItsDeadline() {
            loop.runPass(T0);
            loop.timers(TimeBase.GAME).schedule(T0 + 10_000, handler.spec(1, TimerKind.ACTION));

            final long at = loop.nextPassAt(clock.gameMillis());
            clock.advance(Duration.ofMillis(at - T0));
            loop.runPass(clock.gameMillis());

            assertThat(at).isEqualTo(T0 + 10_000);
            assertThat(handler.fired).containsExactly("1:ACTION@" + (T0 + 10_000));
        }

        private int simulate(Loop target, int millis, Runnable arrivals) {
            int passes = 0;
            for (int ms = 0; ms < millis; ms++) {
                arrivals.run();
                final long now = clock.gameMillis();
                for (int check = 0; check < CHECKS_PER_MILLISECOND && target.nextPassAt(now) <= now; check++) {
                    target.runPass(now);
                    passes++;
                }
                clock.advance(Duration.ofMillis(1));
            }
            return passes;
        }
    }

    @Nested
    class FarDeadlines {
        @Test
        void idleLoop_shouldRunNoPassBeforeTheFirstFarDeadline() {
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final Loop idle = TestLoops.loop("idle", clock, registry);
            for (int i = 0; i < 1_000; i++) {
                idle.timers(TimeBase.GAME).schedule(T0 + 120_000 + i * 97L, handler.spec(i, TimerKind.ACTION));
            }

            int actual = 0;
            while (handler.fired.isEmpty()) {
                final long at = idle.nextPassAt(clock.gameMillis());
                clock.advance(Duration.ofMillis(at - clock.gameMillis()));
                idle.runPass(at);
                actual++;
            }

            assertThat(actual).isEqualTo(1);
            assertThat(handler.fired).containsExactly("0:ACTION@" + (T0 + 120_000));
            assertThat(registry.get("l2nx.engine.pass")
                            .tag("loop", "idle")
                            .timer()
                            .count())
                    .isEqualTo(1);
            assertThat(idle.nextPassAt(clock.gameMillis())).isEqualTo(T0 + 120_097);
        }
    }
}
