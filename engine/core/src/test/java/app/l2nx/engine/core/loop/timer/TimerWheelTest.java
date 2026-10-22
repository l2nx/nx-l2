/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.Loop;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

            assertThat(wheel.nextDueAt()).isEqualTo(101);
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
            assertThat(wheel.nextDueAt()).isEqualTo(11);

            run(11);
            assertThat(handler.fired).containsExactly("2:ACTION@10");
        }

        @Test
        void runDue_shouldFireAtMostTheOverdueCapPerRun_whenOneTickHoldsMore() {
            for (int i = 0; i < 5; i++) {
                wheel.schedule(1, handler.spec(10 + i, TimerKind.ACTION));
            }
            for (int i = 0; i < 5; i++) {
                wheel.schedule(2, handler.spec(20 + i, TimerKind.ACTION));
            }

            final List<Integer> actual = new ArrayList<>();
            for (long now = 10; now < 14; now++) {
                assertThat(wheel.nextDueAt()).isLessThanOrEqualTo(now);
                actual.add(run(now, 3, 10));
            }

            assertThat(actual).containsExactly(3, 3, 3, 1);
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
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void runDue_shouldNotCountEntriesOnTimeAgainstTheOverdueCap() {
            for (int i = 0; i < 5; i++) {
                wheel.schedule(6 + i, handler.spec(30 + i, TimerKind.ACTION));
            }

            assertThat(run(10, 1, 5)).isEqualTo(5);
        }

        @Test
        void runDue_shouldHoldTheOverdueCap_whenLateEntriesShareOneDueTick() {
            run(100);
            for (int target = 5; target >= 1; target--) {
                wheel.schedule(target * 10L, handler.spec(target, TimerKind.ACTION));
            }

            final List<Integer> fired = new ArrayList<>();
            final List<Integer> overdue = new ArrayList<>();
            for (long now = 101; now <= 103; now++) {
                fired.add(run(now, 2, now));
                overdue.add(wheel.getLastOverdue());
            }

            assertThat(fired).containsExactly(2, 2, 1);
            assertThat(overdue).containsExactly(2, 2, 1);
            assertThat(handler.fired)
                    .containsExactly("1:ACTION@10", "2:ACTION@20", "3:ACTION@30", "4:ACTION@40", "5:ACTION@50");
            assertThat(wheel.size()).isZero();
        }

        @Test
        void runDue_shouldNotCountCancelledEntriesAgainstTheOverdueCap() {
            for (int i = 1; i <= 5_000; i++) {
                wheel.schedule(i, handler.spec(1, TimerKind.ACTION));
            }
            handler.setGeneration(1, 1);
            for (int i = 1; i <= 10; i++) {
                wheel.schedule(5_000 + i, handler.spec(2, TimerKind.ACTION));
            }

            assertThat(run(10_000, 4096, 10_000)).isEqualTo(10);
            assertThat(wheel.getLastOverdue()).isEqualTo(10);
            assertThat(wheel.size()).isZero();
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
    class Schedule {
        @ParameterizedTest(name = "deadline {0} after a run at 100 is due at {1}")
        @CsvSource({"50, 101", "100, 101", "101, 101", "150, 150"})
        void schedule_shouldKeepTheDeadlineAndFallDueAfterTheLastRun(long deadline, long expected) {
            run(100);

            final TimerEntry actual = wheel.schedule(deadline, handler.spec(1, TimerKind.ACTION));

            assertThat(actual.getDeadline()).isEqualTo(deadline);
            assertThat(wheel.nextDueAt()).isEqualTo(expected);
            assertThat(run(expected - 1)).isZero();
            assertThat(run(expected)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:ACTION@" + deadline);
        }

        @Test
        void schedule_shouldCountLateEntriesOverdue_whenTheirDeadlineIsBeforeTheLastRun() {
            run(1_000);
            for (int i = 0; i < 20_000; i++) {
                wheel.schedule(500, handler.spec(i, TimerKind.ACTION));
            }

            final List<Integer> actual = new ArrayList<>();
            for (long now = 1_001; now <= 1_005; now++) {
                run(now, 4096, now - 1);
                actual.add(wheel.getLastOverdue());
            }

            assertThat(actual).containsExactly(4096, 4096, 4096, 4096, 3616);
            assertThat(wheel.size()).isZero();
        }

        @Test
        void schedule_shouldFireLateEntriesInDeadlineOrder_whenTheLaterDeadlineWasScheduledFirst() {
            run(1_000);
            wheel.schedule(950, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(900, handler.spec(1, TimerKind.ACTION));

            run(1_001);

            assertThat(handler.fired).containsExactly("1:ACTION@900", "2:ACTION@950");
        }

        @Test
        void schedule_shouldRunOwedTicksBeforeTheEnd_whenATickAndEndPairIsScheduledInThePast() {
            final List<String> actual = new ArrayList<>();
            final TimerHandler effect = new TimerHandler() {
                private boolean ended;

                @Override
                public boolean isCurrent(Loop loop, TimerEntry entry) {
                    return !ended;
                }

                @Override
                public void onTimer(Loop loop, TimerEntry entry) {
                    actual.add(entry.getSpec().kind() + "@" + entry.getDeadline());
                    ended = entry.getSpec().kind() == TimerKind.END;
                }
            };
            run(200);
            final long sequence = wheel.nextSequence();
            wheel.schedule(
                    10,
                    TimerSpec.builder()
                            .handler(effect)
                            .kind(TimerKind.TICK)
                            .period(10)
                            .build(),
                    sequence);
            wheel.schedule(
                    150, TimerSpec.builder().handler(effect).kind(TimerKind.END).build(), sequence);

            assertThat(run(205)).isEqualTo(10);
            assertThat(run(210)).isEqualTo(6);

            final List<String> expected = new ArrayList<>();
            for (long deadline = 10; deadline <= 150; deadline += 10) {
                expected.add("TICK@" + deadline);
            }
            expected.add("END@150");
            assertThat(actual).containsExactlyElementsOf(expected);
            assertThat(wheel.size()).isZero();
        }

        @Test
        void schedule_shouldInterleaveOwedTicksInDeadlineOrder_whenAPeriodicAndAnActionAreScheduledLate() {
            run(200);
            wheel.schedule(10, handler.periodic(1, 10));
            wheel.schedule(15, handler.spec(2, TimerKind.ACTION));

            run(205);

            final List<String> expected = new ArrayList<>(List.of("1:TICK@10", "2:ACTION@15"));
            for (long deadline = 20; deadline <= 100; deadline += 10) {
                expected.add("1:TICK@" + deadline);
            }
            assertThat(handler.fired).containsExactlyElementsOf(expected);
        }

        @Test
        void schedule_shouldLetLaterOverdueTimersFire_whenEarlierTimersRearmAtTheirOwnDeadline() {
            final TimerHandler rearming = (loop, entry) -> {
                handler.onTimer(loop, entry);
                wheel.schedule(entry.getDeadline(), entry.getSpec(), entry.getSequence());
            };
            wheel.schedule(
                    10,
                    TimerSpec.builder()
                            .handler(rearming)
                            .target(1)
                            .kind(TimerKind.ACTION)
                            .build());
            wheel.schedule(
                    20,
                    TimerSpec.builder()
                            .handler(rearming)
                            .target(2)
                            .kind(TimerKind.ACTION)
                            .build());

            for (long now = 100; now < 105; now++) {
                run(now, 1, now);
            }

            assertThat(handler.fired)
                    .containsExactly("1:ACTION@10", "2:ACTION@20", "1:ACTION@10", "2:ACTION@20", "1:ACTION@10");
        }
    }

    @Nested
    class Cancel {
        @Test
        void cancel_shouldRemoveEntryFromWheel_whenArmedInCurrentTurn() {
            final TimerEntry entry = wheel.schedule(10, handler.spec(1, TimerKind.ACTION));

            final boolean actual = wheel.cancel(entry);

            assertThat(actual).isTrue();
            assertThat(wheel.size()).isZero();
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void cancel_shouldRemoveEntryFromWheel_whenItsTurnHasBegun() {
            final TimerEntry entry = wheel.schedule(5_000, handler.spec(1, TimerKind.ACTION));
            run(4_500);

            assertThat(wheel.cancel(entry)).isTrue();

            assertThat(wheel.size()).isZero();
            assertThat(run(5_000)).isZero();
        }

        @Test
        void cancel_shouldRemoveEntryFromBucket_whenArmedInLaterTurn() {
            final TimerEntry far = wheel.schedule(60_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(120_000, handler.spec(2, TimerKind.ACTION));

            assertThat(wheel.cancel(far)).isTrue();

            assertThat(wheel.size()).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(120_000);
            assertThat(run(120_000)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("2:ACTION@120000");
        }

        @Test
        void cancel_shouldMoveNextDueAtToTheNextEntryOfTheTurn_whenItsFirstEntryIsCancelled() {
            final TimerEntry first = wheel.schedule(5_200, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(5_900, handler.spec(2, TimerKind.ACTION));

            wheel.cancel(first);

            assertThat(wheel.nextDueAt()).isEqualTo(5_900);
        }

        @Test
        void cancel_shouldFireTheRestOfTheTurnInOrder_whenTheMiddleEntryOfItsBucketIsCancelled() {
            wheel.schedule(5_900, handler.spec(1, TimerKind.ACTION));
            final TimerEntry middle = wheel.schedule(5_600, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(5_200, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.cancel(middle)).isTrue();

            assertThat(wheel.size()).isEqualTo(2);
            assertThat(run(6_000)).isEqualTo(2);
            assertThat(handler.fired).containsExactly("3:ACTION@5200", "1:ACTION@5900");
        }

        @Test
        void cancel_shouldRemoveTheEntryMovedIntoACancelledSlot() {
            final TimerEntry first = wheel.schedule(5_900, handler.spec(1, TimerKind.ACTION));
            final TimerEntry moved = wheel.schedule(5_200, handler.spec(2, TimerKind.ACTION));

            wheel.cancel(first);

            assertThat(wheel.cancel(moved)).isTrue();
            assertThat(wheel.size()).isZero();
            assertThat(run(6_000)).isZero();
        }

        @Test
        void cancel_shouldRemoveABucketEntry_whenASetBackTookTheLateEntriesBeforeIt() {
            run(TimerWheel.TICKS_PER_WHEEL - 1);
            wheel.schedule(50, handler.spec(1, TimerKind.ACTION));
            final TimerEntry cancelled = wheel.schedule(1_500, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(1_600, handler.spec(3, TimerKind.ACTION));
            wheel.passStarted(400);

            assertThat(wheel.cancel(cancelled)).isTrue();

            assertThat(run(400)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(1_600);
            assertThat(run(1_600)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:ACTION@50", "3:ACTION@1600");
        }

        @Test
        void cancel_shouldNotLeakEntries_whenRearmedAndCancelledRepeatedly() {
            for (int i = 0; i < 10_000; i++) {
                wheel.cancel(wheel.schedule(1_800_000, handler.spec(1, TimerKind.ACTION)));
            }

            assertThat(wheel.size()).isZero();
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void cancel_shouldReturnFalse_whenEntryAlreadyFired() {
            final TimerEntry entry = wheel.schedule(1, handler.spec(1, TimerKind.ACTION));
            run(1);

            assertThat(wheel.cancel(entry)).isFalse();
        }

        @Test
        void cancel_shouldReturnFalse_whenEntryAlreadyCancelled() {
            final TimerEntry entry = wheel.schedule(10, handler.spec(1, TimerKind.ACTION));
            wheel.cancel(entry);

            assertThat(wheel.cancel(entry)).isFalse();
        }
    }

    @Nested
    class PassStarted {
        @ParameterizedTest(name = "deadline {0} after a set back to 400 is due at {1}")
        @CsvSource({"300, 400", "400, 400", "450, 450"})
        void passStarted_shouldLowerTheLastRunTimeBelowTheClock_whenTheClockWentBack(long deadline, long expected) {
            run(1_000);
            wheel.passStarted(400);

            wheel.schedule(deadline, handler.spec(1, TimerKind.ACTION));

            assertThat(run(expected - 1)).isZero();
            assertThat(wheel.nextDueAt()).isEqualTo(expected);
            assertThat(run(expected)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:ACTION@" + deadline);
        }

        @Test
        void passStarted_shouldMakeLateEntriesDueAtTheNewClock_whenTheClockWentBack() {
            run(1_000);
            wheel.schedule(50, handler.spec(1, TimerKind.ACTION));

            wheel.passStarted(400);
            wheel.schedule(300, handler.spec(2, TimerKind.ACTION));

            assertThat(wheel.nextDueAt()).isEqualTo(400);
            assertThat(run(400)).isEqualTo(2);
            assertThat(handler.fired).containsExactly("1:ACTION@50", "2:ACTION@300");
        }

        @ParameterizedTest(name = "with an on-time entry at {0}")
        @ValueSource(longs = {1_500, 2_500})
        void passStarted_shouldMoveALateEntryOutOfTheNextTurn_whenTheClockWentBack(long later) {
            run(TimerWheel.TICKS_PER_WHEEL - 1);
            wheel.schedule(later, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(50, handler.spec(1, TimerKind.ACTION));

            wheel.passStarted(400);

            assertThat(run(400)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(later);
            assertThat(run(later)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:ACTION@50", "2:ACTION@" + later);
            assertThat(wheel.size()).isZero();
        }

        @Test
        void passStarted_shouldFireANewTimerOnTime_whenTheCapLeftLateEntriesBeforeASetBack() {
            run(400_000);
            for (int target = 1; target <= 3; target++) {
                wheel.schedule(100, handler.spec(target, TimerKind.ACTION));
            }
            assertThat(run(400_001, 1, 399_996)).isEqualTo(1);

            wheel.passStarted(100_001);
            wheel.schedule(100_050, handler.spec(4, TimerKind.ACTION));

            assertThat(run(100_001, 1, 99_996)).isEqualTo(1);
            assertThat(run(100_002, 1, 99_997)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(100_050);
            assertThat(run(100_050, 1, 100_045)).isEqualTo(1);
            assertThat(handler.fired)
                    .containsExactly("1:ACTION@100", "2:ACTION@100", "3:ACTION@100", "4:ACTION@100050");
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }

        @ParameterizedTest(name = "pass at {0}")
        @ValueSource(longs = {1_000, 1_005})
        void passStarted_shouldKeepTheLastRunTime_whenTheClockDidNotGoBack(long now) {
            run(1_000);
            wheel.passStarted(now);

            wheel.schedule(500, handler.spec(1, TimerKind.ACTION));

            assertThat(run(1_000)).isZero();
            assertThat(run(1_001)).isEqualTo(1);
        }
    }

    @Nested
    class NextDueAt {
        @Test
        void nextDueAt_shouldBeTheEarliestOccupiedTick() {
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);

            wheel.schedule(30, handler.spec(1, TimerKind.ACTION));
            assertThat(wheel.nextDueAt()).isEqualTo(30);

            run(30);
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void nextDueAt_shouldBeTheDeadlineOfAnEntryBehindTheCursor_whenTheTimeWentBack() {
            run(1_000);
            run(400);
            wheel.schedule(500, handler.spec(1, TimerKind.ACTION));

            assertThat(wheel.nextDueAt()).isEqualTo(500);
            assertThat(run(499)).isZero();
            assertThat(run(500)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    class FarDeadline {
        @Test
        void nextDueAt_shouldBeTheRealDeadline_whenTheTimerIsMinutesAhead() {
            wheel.schedule(300_000, handler.spec(1, TimerKind.ACTION));

            assertThat(wheel.nextDueAt()).isEqualTo(300_000);
            assertThat(wheel.size()).isEqualTo(1);
        }

        @Test
        void nextDueAt_shouldBeTheNextFarDeadline_whenTheNearTimersHaveFired() {
            wheel.schedule(300_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(1_500, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(400, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDueAt()).isEqualTo(400);
            run(400);
            assertThat(wheel.nextDueAt()).isEqualTo(1_500);
            run(1_500);
            assertThat(wheel.nextDueAt()).isEqualTo(300_000);
            assertThat(handler.fired).containsExactly("3:ACTION@400", "2:ACTION@1500");
        }

        @Test
        void nextDueAt_shouldWakeOnlyAtTheDeadline_whenItIsWheelTurnsAhead() {
            wheel.schedule(5_000, handler.spec(1, TimerKind.ACTION));

            int wakes = 0;
            while (handler.fired.isEmpty()) {
                final long actual = wheel.nextDueAt();
                assertThat(actual).isEqualTo(5_000);
                run(actual);
                wakes++;
            }

            assertThat(wakes).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1:ACTION@5000");
        }

        @Test
        void nextDueAt_shouldBeTheEarliestDeadlineOfATurn_whenItsTimersArriveOutOfOrder() {
            wheel.schedule(5_900, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(5_200, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(5_600, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDueAt()).isEqualTo(5_200);
            run(5_200);
            assertThat(wheel.nextDueAt()).isEqualTo(5_600);
            assertThat(handler.fired).containsExactly("2:ACTION@5200");
        }

        @Test
        void nextDueAt_shouldWakeAtEachFarDeadlineInOrder_whenTurnsArriveOutOfOrder() {
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
            while (wheel.nextDueAt() != Long.MAX_VALUE) {
                assertThat(run(wheel.nextDueAt())).isEqualTo(1);
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

            assertThat(wheel.nextDueAt()).isEqualTo(900);
            run(900);
            assertThat(wheel.nextDueAt()).isEqualTo(4_999);
            assertThat(run(4_998)).isZero();
            assertThat(run(4_999)).isEqualTo(1);
            wheel.schedule(5_000, handler.spec(5, TimerKind.ACTION), reserved);
            assertThat(wheel.nextDueAt()).isEqualTo(5_000);
            assertThat(run(5_000)).isEqualTo(3);

            assertThat(handler.fired)
                    .containsExactly(
                            "4:ACTION@900", "3:ACTION@4999", "5:ACTION@5000", "1:ACTION@5000", "2:ACTION@5000");
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
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
            assertThat(wheel.nextDueAt()).isEqualTo(12_000);
        }

        @Test
        void runDue_shouldHoldTheOverdueCapAcrossWheelTurns() {
            wheel.schedule(2_000, handler.spec(1, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(5_000, handler.spec(3, TimerKind.ACTION));

            assertThat(run(10_000, 1, 10_000)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isLessThanOrEqualTo(10_000);
            assertThat(run(10_000, 1, 10_000)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isLessThanOrEqualTo(10_001);
            assertThat(run(10_001, 1, 10_000)).isEqualTo(1);

            assertThat(handler.fired).containsExactly("1:ACTION@2000", "2:ACTION@5000", "3:ACTION@5000");
        }

        @Test
        void runDue_shouldDropAFarTimerWhoseGenerationChanged() {
            wheel.schedule(300_000, handler.spec(1, TimerKind.END));
            handler.setGeneration(1, 1);

            assertThat(run(300_000)).isZero();
            assertThat(handler.fired).isEmpty();
            assertThat(wheel.size()).isZero();
            assertThat(wheel.nextDueAt()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void runDue_shouldRearmAFarPeriodicTimerAtItsNextDeadline() {
            wheel.schedule(5_000, handler.periodic(1, 5_000));

            for (int i = 0; i < 3; i++) {
                run(wheel.nextDueAt());
            }

            assertThat(handler.fired).containsExactly("1:TICK@5000", "1:TICK@10000", "1:TICK@15000");
            assertThat(wheel.nextDueAt()).isEqualTo(20_000);
            assertThat(wheel.size()).isEqualTo(1);
        }

        @Test
        void runDue_shouldKeepAFarTimer_whenTheTimeWentBack() {
            wheel.schedule(10_000, handler.spec(1, TimerKind.ACTION));
            run(5_000);
            run(400);
            wheel.schedule(500, handler.spec(2, TimerKind.ACTION));
            wheel.schedule(7_000, handler.spec(3, TimerKind.ACTION));

            assertThat(wheel.nextDueAt()).isEqualTo(500);
            assertThat(run(500)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(7_000);
            assertThat(run(6_999)).isZero();
            assertThat(run(7_000)).isEqualTo(1);
            assertThat(wheel.nextDueAt()).isEqualTo(10_000);
            assertThat(run(10_000)).isEqualTo(1);

            assertThat(handler.fired).containsExactly("2:ACTION@500", "3:ACTION@7000", "1:ACTION@10000");
        }
    }
}
