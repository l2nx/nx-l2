/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// Handlers ignore the loop argument, so the tests pass null.
class TimerQueueTest {
    private final List<RuntimeException> failures = new ArrayList<>();
    private final TimerQueue queue = new TimerQueue((phase, source, failure) -> failures.add(failure));
    private final RecordingTimerHandler handler = new RecordingTimerHandler();

    private int run(long now) {
        return run(now, Integer.MAX_VALUE, Long.MIN_VALUE);
    }

    private int run(long now, int overdueCap, long lateBefore) {
        return queue.runDue(null, now, overdueCap, lateBefore);
    }

    @Nested
    class RunDue {
        @Test
        void runDue_shouldFireInDeadlineThenSequenceOrder() {
            queue.schedule(10, handler.spec(1));
            queue.schedule(10, handler.spec(2));
            queue.schedule(5, handler.spec(3));
            queue.schedule(11, handler.spec(4));

            assertThat(run(10)).isEqualTo(3);
            assertThat(handler.fired).containsExactly("3@5", "1@10", "2@10");

            assertThat(run(11)).isEqualTo(1);
            assertThat(handler.fired).endsWith("4@11");
        }

        @Test
        void runDue_shouldDropEntriesWhoseGenerationChanged() {
            queue.schedule(10, handler.spec(1));
            handler.setGeneration(1, 1);
            queue.schedule(10, handler.spec(1).toBuilder().generation(1).build());

            assertThat(run(10)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1@10");
            assertThat(queue.size()).isZero();
        }

        @Test
        void runDue_shouldFireAtMostTheOverdueCapAndKeepTheRestInOrder_whenEntriesAreLate() {
            for (int i = 1; i <= 5; i++) {
                queue.schedule(i, handler.spec(i));
            }
            queue.schedule(100, handler.spec(6));

            assertThat(run(100, 2, 95)).isEqualTo(2);
            assertThat(handler.fired).containsExactly("1@1", "2@2");
            assertThat(queue.getLastOverdue()).isEqualTo(2);
            assertThat(queue.nextDeadline()).isEqualTo(3);

            assertThat(run(101, 2, 96)).isEqualTo(2);
            assertThat(run(102, 2, 97)).isEqualTo(2);
            assertThat(handler.fired).containsExactly("1@1", "2@2", "3@3", "4@4", "5@5", "6@100");
        }

        @Test
        void runDue_shouldNotCountCancelledOrStaleEntriesAgainstTheOverdueCap() {
            final TimerEntry cancelled = queue.schedule(1, handler.spec(1));
            handler.setGeneration(2, 1);
            queue.schedule(2, handler.spec(2));
            queue.schedule(3, handler.spec(3));
            queue.cancel(cancelled);

            assertThat(run(100, 1, 95)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("3@3");
        }

        @ParameterizedTest(name = "handler delay {0}")
        @ValueSource(longs = {0, -5})
        void runDue_shouldArmAnEntryScheduledByAHandlerAfterTheRun_whenItsDeadlineIsDue(long delay) {
            final TimerHandler rescheduling =
                    (loop, entry) -> queue.schedule(entry.getDeadline() + delay, handler.spec(2));
            queue.schedule(10, TimerSpec.builder().handler(rescheduling).build());

            assertThat(run(10)).isEqualTo(1);
            assertThat(handler.fired).isEmpty();
            assertThat(queue.nextDeadline()).isEqualTo(11);

            run(11);
            assertThat(handler.fired).containsExactly("2@11");
        }

        @Test
        void schedule_shouldReturnTheEffectiveDeadline_whenArmedDuringARun() {
            final List<Long> actual = new ArrayList<>();
            final TimerHandler rescheduling = (loop, entry) -> actual.add(
                    queue.schedule(entry.getDeadline(), handler.spec(2)).getDeadline());
            queue.schedule(10, TimerSpec.builder().handler(rescheduling).build());

            run(10);

            assertThat(actual).containsExactly(11L);
        }

        @Test
        void runDue_shouldFireTheEntriesBehindARearmingHandler_whenTheOverdueCapIsReached() {
            final TimerHandler stuck = (loop, entry) -> queue.schedule(entry.getDeadline(), entry.getSpec());
            queue.schedule(1, TimerSpec.builder().handler(stuck).build());
            queue.schedule(2, handler.spec(2));

            run(100, 1, 95);
            run(101, 1, 96);

            assertThat(handler.fired).containsExactly("2@2");
        }

        @Test
        void runDue_shouldKeepAPastDeadline_whenScheduledOutsideARun() {
            run(100);

            queue.schedule(40, handler.spec(1));

            assertThat(queue.nextDeadline()).isEqualTo(40);
        }

        @Test
        void runDue_shouldFireALateScheduleAtTheNextRunAndCountItOverdue() {
            run(100);
            queue.schedule(50, handler.spec(1));

            assertThat(queue.nextDeadline()).isEqualTo(50);
            assertThat(run(105, 4096, 100)).isEqualTo(1);
            assertThat(queue.getLastOverdue()).isEqualTo(1);
        }

        @Test
        void runDue_shouldFireNothingEarly_whenTheClockWentBack() {
            queue.schedule(200, handler.spec(1));
            run(150);

            assertThat(run(50)).isZero();
            assertThat(run(200)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1@200");
        }

        @Test
        void runDue_shouldIsolateAFailingHandler() {
            queue.schedule(
                    1,
                    TimerSpec.builder()
                            .handler((loop, entry) -> {
                                throw new IllegalStateException("broken");
                            })
                            .build());
            queue.schedule(2, handler.spec(2));

            assertThat(run(2)).isEqualTo(2);
            assertThat(failures)
                    .singleElement()
                    .extracting(Throwable::getMessage)
                    .isEqualTo("broken");
            assertThat(handler.fired).containsExactly("2@2");
        }
    }

    @Nested
    class FixedRate {
        @Test
        void runDue_shouldCarryTheRemainder_whenAPassIsLateByLessThanAPeriod() {
            queue.schedule(333, handler.periodic(1, 333));

            run(340);
            run(666);
            run(1_000);

            assertThat(handler.fired).containsExactly("1@333", "1@666", "1@999");
        }

        @Test
        void runDue_shouldSkipMissedRunsAndKeepThePhase_whenAPassIsLateBySeveralPeriods() {
            queue.schedule(100, handler.periodic(1, 100));

            assertThat(run(450)).isEqualTo(1);
            assertThat(handler.fired).containsExactly("1@100");
            assertThat(queue.nextDeadline()).isEqualTo(500);
        }

        @Test
        void runDue_shouldStopAFixedRateEntry_whenItsHandlerCancelsIt() {
            final List<Boolean> actual = new ArrayList<>();
            queue.schedule(
                    10,
                    TimerSpec.builder()
                            .handler((loop, entry) -> actual.add(queue.cancel(entry)))
                            .period(10)
                            .build());

            run(10);
            run(20);

            assertThat(actual).containsExactly(true);
            assertThat(queue.size()).isZero();
        }

        @Test
        void runDue_shouldStopAFixedRateEntry_whenItsGenerationChanges() {
            queue.schedule(10, handler.periodic(1, 10));
            run(10);
            handler.setGeneration(1, 1);

            run(20);

            assertThat(handler.fired).containsExactly("1@10");
            assertThat(queue.size()).isZero();
        }
    }

    @Nested
    class NextFixedRateDeadline {
        @ParameterizedTest(name = "deadline {0}, period {1}, now {2} -> {3}")
        @CsvSource({
            "100, 100, 100, 200",
            "100, 100, 199, 200",
            "100, 100, 200, 300",
            "100, 100, 450, 500",
            "333, 333, 340, 666"
        })
        void nextFixedRateDeadline_shouldBeTheFirstGridPointAfterNow(
                long deadline, long period, long now, long expected) {
            assertThat(TimerQueue.nextFixedRateDeadline(deadline, period, now)).isEqualTo(expected);
        }
    }

    @Nested
    class Cancel {
        @Test
        void cancel_shouldReturnTrueOnceAndSkipTheEntry() {
            final TimerEntry entry = queue.schedule(10, handler.spec(1));
            queue.schedule(20, handler.spec(2));

            assertThat(queue.cancel(entry)).isTrue();
            assertThat(queue.cancel(entry)).isFalse();
            assertThat(queue.size()).isEqualTo(1);
            assertThat(queue.nextDeadline()).isEqualTo(20);

            run(20);
            assertThat(handler.fired).containsExactly("2@20");
        }

        @Test
        void cancel_shouldThrow_whenTheEntryBelongsToAnotherQueue() {
            final TimerQueue other = new TimerQueue((phase, source, failure) -> failures.add(failure));
            final TimerEntry entry = other.schedule(10, handler.spec(1));

            assertThatIllegalArgumentException().isThrownBy(() -> queue.cancel(entry));
            assertThat(other.size()).isEqualTo(1);
        }

        @Test
        void cancel_shouldReturnFalse_whenTheEntryAlreadyFired() {
            final TimerEntry entry = queue.schedule(10, handler.spec(1));
            run(10);

            assertThat(queue.cancel(entry)).isFalse();
        }

        @Test
        void cancel_shouldPurgeTheHeap_whenCancelledEntriesAreMoreThanHalfOfIt() {
            final List<TimerEntry> entries = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                entries.add(queue.schedule(100 + i, handler.spec(i)));
            }
            for (int i = 0; i < 5; i++) {
                queue.cancel(entries.get(i));
            }
            assertThat(queue.heapSize()).isEqualTo(10);

            queue.cancel(entries.get(5));

            assertThat(queue.heapSize()).isEqualTo(4);
            assertThat(queue.size()).isEqualTo(4);
        }

        @Test
        void cancel_shouldKeepTheHeapBounded_whenATimerIsRearmedAndCancelledRepeatedly() {
            queue.schedule(1_000_000, handler.spec(0));
            TimerEntry entry = queue.schedule(100, handler.spec(1));

            for (int i = 0; i < 10_000; i++) {
                queue.cancel(entry);
                entry = queue.schedule(100 + i, handler.spec(1));
            }

            assertThat(queue.heapSize()).isLessThanOrEqualTo(4);
            assertThat(queue.size()).isEqualTo(2);
        }
    }

    @Nested
    class CancelStale {
        @Test
        void cancelStale_shouldCancelTheSupersededEntriesOfTheHandlerAtOnce() {
            final RecordingTimerHandler otherHandler = new RecordingTimerHandler();
            queue.schedule(10, handler.spec(1));
            queue.schedule(20, handler.spec(1));
            queue.schedule(30, handler.spec(2));
            queue.schedule(15, otherHandler.spec(1));
            handler.setGeneration(1, 1);
            otherHandler.setGeneration(1, 1);

            final int actual = queue.cancelStale(null, handler);

            assertThat(actual).isEqualTo(2);
            assertThat(queue.size()).isEqualTo(2);
            assertThat(queue.nextDeadline()).isEqualTo(15);
        }

        @Test
        void cancelStale_shouldLetThePurgeReclaimTheEntries_whenTheyAreMostOfTheHeap() {
            for (int i = 0; i < 3; i++) {
                queue.schedule(100 + i, handler.spec(1));
            }
            queue.schedule(200, handler.spec(2));
            handler.setGeneration(1, 1);

            queue.cancelStale(null, handler);

            assertThat(queue.heapSize()).isEqualTo(1);
            assertThat(queue.size()).isEqualTo(1);
        }
    }

    @Nested
    class NextDeadline {
        @Test
        void nextDeadline_shouldBeMaxValue_whenNothingIsPending() {
            assertThat(queue.nextDeadline()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void nextDeadline_shouldBeTheRealDeadline_whenTheTimerIsMinutesAhead() {
            queue.schedule(600_000, handler.spec(1));

            assertThat(queue.nextDeadline()).isEqualTo(600_000);
        }
    }
}
