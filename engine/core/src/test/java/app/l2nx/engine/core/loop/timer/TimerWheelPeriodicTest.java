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

class TimerWheelPeriodicTest {
    private final TimerWheel wheel = new TimerWheel(0, (phase, source, failure) -> {
        throw failure;
    });
    private final RecordingTimerHandler handler = new RecordingTimerHandler();

    private int run(long now) {
        return wheel.runDue(null, now, 4096, 10, Long.MIN_VALUE);
    }

    @Nested
    class PeriodicEntry {
        @Test
        void periodicEntry_shouldCarryTheRemainderInsteadOfDriftingToThePass() {
            wheel.schedule(333, handler.periodic(1, 333));

            run(340);
            run(700);
            run(1_000);

            assertThat(handler.fired).containsExactly("1:TICK@333", "1:TICK@666", "1:TICK@999");
        }

        @Test
        void periodicEntry_shouldStopWhenItsGenerationChanges() {
            final TimerHandler cleansing = new TimerHandler() {
                private int ticks;

                @Override
                public boolean isCurrent(Loop loop, TimerEntry entry) {
                    return ticks < 3;
                }

                @Override
                public void onTimer(Loop loop, TimerEntry entry) {
                    ticks++;
                }
            };
            wheel.schedule(
                    10,
                    TimerSpec.builder()
                            .handler(cleansing)
                            .kind(TimerKind.TICK)
                            .period(10)
                            .build());

            assertThat(run(100)).isEqualTo(3);
            assertThat(wheel.size()).isZero();
        }
    }

    @Nested
    class OwedTicks {
        @Test
        void latePass_shouldRunAtMostTenOwedTicksPerEntryAndContinueNextPass() {
            wheel.schedule(10, handler.periodic(1, 10));

            assertThat(run(200)).isEqualTo(10);
            assertThat(handler.fired).first().isEqualTo("1:TICK@10");
            assertThat(handler.fired).last().isEqualTo("1:TICK@100");
            assertThat(wheel.nextDueAt()).isEqualTo(201);

            assertThat(run(205)).isEqualTo(10);
            assertThat(handler.fired).last().isEqualTo("1:TICK@200");

            assertThat(run(210)).isEqualTo(1);
            assertThat(handler.fired).hasSize(21).last().isEqualTo("1:TICK@210");
        }

        @Test
        void owedTicks_shouldInterleaveWithOtherEntriesInDeadlineOrder() {
            wheel.schedule(10, handler.periodic(1, 10));
            wheel.schedule(25, handler.spec(2, TimerKind.ACTION));

            run(40);

            assertThat(handler.fired)
                    .containsExactly("1:TICK@10", "1:TICK@20", "2:ACTION@25", "1:TICK@30", "1:TICK@40");
        }

        @Test
        void owedTickCap_shouldNotLetTheEndOvertakeTicksStillOwed() {
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
                    if (entry.getSpec().kind() == TimerKind.END) {
                        ended = true;
                    }
                }
            };
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

            assertThat(run(200)).isEqualTo(10);
            assertThat(actual).last().isEqualTo("TICK@100");

            assertThat(run(205)).isEqualTo(6);
            assertThat(actual).hasSize(16).endsWith("TICK@140", "TICK@150", "END@150");
            assertThat(wheel.size()).isZero();
        }
    }
}
