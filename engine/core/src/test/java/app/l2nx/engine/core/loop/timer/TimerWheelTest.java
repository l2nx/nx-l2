/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// The wheel starts at 0; handlers ignore the loop argument, so the tests pass null.
class TimerWheelTest {
    private final List<RuntimeException> failures = new ArrayList<>();
    private final TimerWheel wheel = new TimerWheel(0, (phase, source, failure) -> failures.add(failure));
    private final RecordingTimerHandler handler = new RecordingTimerHandler();

    private int run(long now) {
        return run(now, 4096, Long.MIN_VALUE);
    }

    private int run(long now, int overdueCap, long lateBefore) {
        return wheel.runDue(null, now, overdueCap, 10, lateBefore);
    }

    @Nested
    class RunDue {
        @Test
        void runDue_shouldFireInDeadlineThenSequenceOrder() {
            wheel.schedule(10, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(10, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(5, handler.spec(3, TimerKind.ACTION));
            wheel.schedule(11, handler.spec(4, TimerKind.ACTION));

            assertThat(run(10)).isEqualTo(3);
            assertThat(handler.fired).containsExactly("3:ACTION@5", "1:ACTION@10", "2:ACTION@10");

            assertThat(run(11)).isEqualTo(1);
            assertThat(handler.fired).endsWith("4:ACTION@11");
        }

        @Test
        void runDue_shouldRunTheTickBeforeTheEndThatSharesItsSequence() {
            wheel.schedule(20, handler.spec(3, TimerKind.END));
            final long effect = wheel.nextSequence();
            wheel.schedule(20, handler.spec(1, TimerKind.END), effect);
            wheel.schedule(20, handler.spec(1, TimerKind.TICK), effect);
            wheel.schedule(20, handler.spec(2, TimerKind.TICK));

            run(20);

            assertThat(handler.fired).containsExactly("3:END@20", "1:TICK@20", "1:END@20", "2:TICK@20");
        }

        @Test
        void runDue_shouldDropEntriesWhoseGenerationChanged() {
            wheel.schedule(10, handler.spec(1, TimerKind.END));
            handler.setGeneration(1, 1);
            wheel.schedule(
                    10, handler.spec(1, TimerKind.END).toBuilder().generation(1).build());

            assertThat(run(10)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:END@10");
            assertThat(wheel.size()).isZero();
        }

        @Test
        @Timeout(1)
        void runDue_shouldSkipIdleTicksInsteadOfPollingEachMillisecond() {
            wheel.schedule(1_000_000, handler.spec(1, TimerKind.ACTION));

            assertThat(run(999_999)).isZero();
            assertThat(run(1_000_000)).isEqualTo(1);
        }

        @Test
        void runDue_shouldFireALateEntryAtTheNextRunAndCountItOverdue() {
            run(100);
            wheel.schedule(50, handler.spec(1, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(101);
            assertThat(run(105, 4096, 100)).isEqualTo(1);
            assertThat(wheel.getLastOverdue()).isEqualTo(1);
        }

        @Test
        void runDue_shouldLeaveEntriesScheduledWhileFiringForTheNextRun() {
            final TimerHandler rescheduling =
                    (loop, entry) -> wheel.schedule(entry.getDeadline(), handler.spec(2, TimerKind.ACTION));
            wheel.schedule(
                    10,
                    TimerSpec.builder()
                            .handler(rescheduling)
                            .kind(TimerKind.ACTION)
                            .build());

            assertThat(run(10)).isEqualTo(1);
            assertThat(handler.fired).isEmpty();
            assertThat(wheel.nextDeadline()).isEqualTo(11);

            run(11);
            assertThat(handler.fired).containsExactly("2:ACTION@10");
        }

        @Test
        void runDue_shouldStopAtATickBoundaryWhenTheOverdueCapIsReached() {
            for (int i = 0; i < 5; i++) {
                wheel.schedule(1, handler.spec(10 + i, TimerKind.ACTION));
            }
            for (int i = 0; i < 5; i++) {
                wheel.schedule(2, handler.spec(20 + i, TimerKind.ACTION));
            }

            assertThat(run(10, 3, 10)).isEqualTo(5);
            assertThat(handler.fired).allMatch(fired -> fired.endsWith("@1"));
            assertThat(wheel.nextDeadline()).isLessThanOrEqualTo(10);

            assertThat(run(10, 3, 10)).isEqualTo(5);
            assertThat(handler.fired)
                    .containsExactly(
                            "10:ACTION@1",
                            "11:ACTION@1",
                            "12:ACTION@1",
                            "13:ACTION@1",
                            "14:ACTION@1",
                            "20:ACTION@2",
                            "21:ACTION@2",
                            "22:ACTION@2",
                            "23:ACTION@2",
                            "24:ACTION@2");
            assertThat(wheel.nextDeadline()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void runDue_shouldNotCountEntriesOnTimeAgainstTheOverdueCap() {
            for (int i = 0; i < 5; i++) {
                wheel.schedule(6 + i, handler.spec(30 + i, TimerKind.ACTION));
            }

            assertThat(run(10, 1, 5)).isEqualTo(5);
        }

        @Test
        void runDue_shouldFireAnEntryScheduledAtTheSameTimeAgain() {
            run(10);
            wheel.schedule(10, handler.spec(1, TimerKind.ACTION));

            assertThat(run(10)).isEqualTo(1);
        }

        @Test
        void runDue_shouldIsolateAFailingHandler() {
            final TimerHandler failing = (loop, entry) -> {
                throw new IllegalStateException("boom");
            };
            wheel.schedule(
                    5,
                    TimerSpec.builder().handler(failing).kind(TimerKind.ACTION).build());
            wheel.schedule(5, handler.spec(2, TimerKind.ACTION));

            run(5);

            assertThat(failures).hasSize(1);
            assertThat(handler.fired).containsExactly("2:ACTION@5");
        }
    }

    @Nested
    class NextDeadline {
        @Test
        void nextDeadline_shouldBeTheEarliestOccupiedTick() {
            assertThat(wheel.nextDeadline()).isEqualTo(Long.MAX_VALUE);

            wheel.schedule(30, handler.spec(1, TimerKind.ACTION));
            assertThat(wheel.nextDeadline()).isEqualTo(30);

            run(30);
            assertThat(wheel.nextDeadline()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void nextDeadline_shouldBeTheDeadlineOfAnEntryBehindTheCursor_whenTheTimeWentBack() {
            run(1_000);
            run(400);
            wheel.schedule(500, handler.spec(1, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(500);
            assertThat(run(499)).isZero();
            assertThat(run(500)).isEqualTo(1);
            assertThat(wheel.nextDeadline()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    class FarDeadline {
        @Test
        void nextDeadline_shouldBeTheRealDeadline_whenTheTimerIsMinutesAhead() {
            wheel.schedule(300_000, handler.spec(1, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(300_000);
            assertThat(wheel.size()).isEqualTo(1);
        }

        @Test
        void nextDeadline_shouldBeTheNextFarDeadline_whenTheNearTimersHaveFired() {
            wheel.schedule(300_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(1_500, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(400, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(400);
            run(400);
            assertThat(wheel.nextDeadline()).isEqualTo(1_500);
            run(1_500);
            assertThat(wheel.nextDeadline()).isEqualTo(300_000);
            assertThat(handler.fired).containsExactly("3:ACTION@400", "2:ACTION@1500");
        }

        @Test
        void nextDeadline_shouldWakeOnlyAtTheDeadline_whenItIsWheelTurnsAhead() {
            wheel.schedule(5_000, handler.spec(1, TimerKind.ACTION));

            int wakes = 0;
            while (handler.fired.isEmpty()) {
                final long actual = wheel.nextDeadline();
                assertThat(actual).isEqualTo(5_000);
                run(actual);
                wakes++;
            }

            assertThat(wakes).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:ACTION@5000");
        }

        @Test
        void nextDeadline_shouldBeTheEarliestDeadlineOfATurn_whenItsTimersArriveOutOfOrder() {
            wheel.schedule(5_900, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(5_200, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(5_600, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(5_200);
            run(5_200);
            assertThat(wheel.nextDeadline()).isEqualTo(5_600);
            assertThat(handler.fired).containsExactly("2:ACTION@5200");
        }

        @Test
        void nextDeadline_shouldWakeAtEachFarDeadlineInOrder_whenTurnsArriveOutOfOrder() {
            final List<String> expected = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                final int turn = (i * 37) % 50 + 2;
                wheel.schedule(turn * 1_000L + i, handler.spec(turn, TimerKind.ACTION));
            }
            for (int turn = 2; turn < 52; turn++) {
                final int i = (turn - 2) * 23 % 50;
                expected.add(turn + ":ACTION@" + (turn * 1_000L + i));
            }

            int actual = 0;
            while (wheel.nextDeadline() != Long.MAX_VALUE) {
                assertThat(run(wheel.nextDeadline())).isEqualTo(1);
                actual++;
            }

            assertThat(actual).isEqualTo(50);
            assertThat(handler.fired).containsExactlyElementsOf(expected);
        }

        @Test
        void runDue_shouldFireFarTimersAtTheirDeadlineInOrderWithNearTimers() {
            final long reserved = wheel.nextSequence();
            wheel.schedule(900, handler.spec(4, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(4_999, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(900);
            run(900);
            assertThat(wheel.nextDeadline()).isEqualTo(4_999);
            assertThat(run(4_998)).isZero();
            assertThat(run(4_999)).isEqualTo(1);
            wheel.schedule(5_000, handler.spec(5, TimerKind.ACTION), reserved);
            assertThat(wheel.nextDeadline()).isEqualTo(5_000);
            assertThat(run(5_000)).isEqualTo(3);

            assertThat(handler.fired)
                    .containsExactly(
                            "4:ACTION@900", "3:ACTION@4999", "5:ACTION@5000", "1:ACTION@5000", "2:ACTION@5000");
            assertThat(wheel.nextDeadline()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void runDue_shouldFireDeadlinesOfSeveralWheelTurnsInOrder_whenOneRunCoversThem() {
            wheel.schedule(9_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(2_000, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(100, handler.spec(3, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(4, TimerKind.ACTION));
            wheel.schedule(12_000, handler.spec(5, TimerKind.ACTION));

            assertThat(run(10_000)).isEqualTo(4);

            assertThat(handler.fired)
                    .containsExactly("3:ACTION@100", "2:ACTION@2000", "4:ACTION@5000", "1:ACTION@9000");
            assertThat(wheel.nextDeadline()).isEqualTo(12_000);
        }

        @Test
        void runDue_shouldHoldTheOverdueCapAcrossWheelTurns() {
            wheel.schedule(2_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(3, TimerKind.ACTION));

            assertThat(run(10_000, 1, 10_000)).isEqualTo(1);
            assertThat(wheel.nextDeadline()).isLessThanOrEqualTo(10_000);
            assertThat(run(10_000, 1, 10_000)).isEqualTo(2);

            assertThat(handler.fired).containsExactly("1:ACTION@2000", "2:ACTION@5000", "3:ACTION@5000");
        }

        @Test
        void runDue_shouldDropAFarTimerWhoseGenerationChanged() {
            wheel.schedule(300_000, handler.spec(1, TimerKind.END));
            handler.setGeneration(1, 1);

            assertThat(run(300_000)).isZero();
            assertThat(handler.fired).isEmpty();
            assertThat(wheel.size()).isZero();
            assertThat(wheel.nextDeadline()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void runDue_shouldRearmAFarPeriodicTimerAtItsNextDeadline() {
            wheel.schedule(5_000, handler.periodic(1, 5_000));

            for (int i = 0; i < 3; i++) {
                run(wheel.nextDeadline());
            }

            assertThat(handler.fired).containsExactly("1:TICK@5000", "1:TICK@10000", "1:TICK@15000");
            assertThat(wheel.nextDeadline()).isEqualTo(20_000);
            assertThat(wheel.size()).isEqualTo(1);
        }

        @Test
        void runDue_shouldKeepAFarTimer_whenTheTimeWentBack() {
            wheel.schedule(10_000, handler.spec(1, TimerKind.ACTION));
            run(5_000);
            run(400);
            wheel.schedule(500, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(7_000, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDeadline()).isEqualTo(500);
            assertThat(run(500)).isEqualTo(1);
            assertThat(wheel.nextDeadline()).isEqualTo(7_000);
            assertThat(run(6_999)).isZero();
            assertThat(run(7_000)).isEqualTo(1);
            assertThat(wheel.nextDeadline()).isEqualTo(10_000);
            assertThat(run(10_000)).isEqualTo(1);

            assertThat(handler.fired).containsExactly("2:ACTION@500", "3:ACTION@7000", "1:ACTION@10000");
        }
    }
}
