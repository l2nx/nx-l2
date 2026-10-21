/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.LongSummaryStatistics;
import java.util.SplittableRandom;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActionScheduleTest {
    private static final ActionSchedule SCHEDULE = ActionSchedule.builder()
            .interval(Duration.ofMillis(100))
            .jitter(Duration.ofMillis(20))
            .build();

    @Nested
    class Validate {
        @ParameterizedTest(name = "interval {0} ms, jitter {1} ms")
        @CsvSource({"0, 0", "-1, 0", "100, -1", "100, 100", "100, 150"})
        void constructor_shouldReject_whenIntervalOrJitterIsOutOfRange(long intervalMillis, long jitterMillis) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ActionSchedule.builder()
                            .interval(Duration.ofMillis(intervalMillis))
                            .jitter(Duration.ofMillis(jitterMillis))
                            .build());
        }
    }

    @Nested
    class Delays {
        @Test
        void nextDelayNanos_shouldStayWithinIntervalPlusMinusJitter() {
            final SplittableRandom random = new SplittableRandom(7);

            final LongSummaryStatistics actual = LongStream.range(0, 10_000)
                    .map(i -> SCHEDULE.nextDelayNanos(random))
                    .summaryStatistics();

            assertThat(actual.getMin()).isBetween(80_000_000L, 82_000_000L);
            assertThat(actual.getMax()).isBetween(118_000_000L, 120_000_000L);
        }

        @Test
        void firstDelayNanos_shouldStayWithinOneInterval() {
            final SplittableRandom random = new SplittableRandom(7);

            final LongSummaryStatistics actual = LongStream.range(0, 10_000)
                    .map(i -> SCHEDULE.firstDelayNanos(random))
                    .summaryStatistics();

            assertThat(actual.getMin()).isGreaterThanOrEqualTo(0L);
            assertThat(actual.getMax()).isLessThan(100_000_000L);
        }

        @Test
        void nextDelayNanos_shouldBeTheInterval_whenJitterIsZero() {
            final ActionSchedule exact =
                    SCHEDULE.toBuilder().jitter(Duration.ZERO).build();

            assertThat(exact.nextDelayNanos(new SplittableRandom(7))).isEqualTo(100_000_000L);
        }
    }
}
