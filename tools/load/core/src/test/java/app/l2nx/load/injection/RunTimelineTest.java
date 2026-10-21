/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.load.injection.InjectionProfile.InjectionProfileBuilder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class RunTimelineTest {
    private static final long START = -5_000_000_000L;
    private static final InjectionProfile PROFILE = InjectionProfile.builder()
            .bots(4)
            .ramp(Duration.ofSeconds(1))
            .warmUp(Duration.ofSeconds(2))
            .steady(Duration.ofSeconds(10))
            .rampDown(Duration.ofSeconds(4))
            .build();

    private final RunTimeline timeline = new RunTimeline(PROFILE, () -> START);

    private static long at(long millis) {
        return START + TimeUnit.MILLISECONDS.toNanos(millis);
    }

    @Nested
    class Offsets {
        @ParameterizedTest(name = "load bot {0} starts at {1} ms")
        @CsvSource({"0, 0", "1, 250", "2, 500", "3, 750"})
        void startAt_shouldSpreadLoadBotsEvenlyOverTheRamp(int index, long expectedMillis) {
            assertThat(timeline.startAt(index)).isEqualTo(at(expectedMillis));
        }

        @ParameterizedTest(name = "load bot {0} leaves at {1} ms")
        @CsvSource({"0, 13000", "1, 14000", "2, 15000", "3, 16000"})
        void leaveAt_shouldSpreadLoadBotsEvenlyOverTheRampDown(int index, long expectedMillis) {
            assertThat(timeline.leaveAt(index)).isEqualTo(at(expectedMillis));
        }
    }

    @Nested
    class Steady {
        @ParameterizedTest(name = "{0} ms in steady: {1}")
        @CsvSource({"2999, false", "3000, true", "12999, true", "13000, false"})
        void inSteady_shouldIncludeStartAndExcludeEnd(long millis, boolean expected) {
            assertThat(timeline.inSteady(at(millis))).isEqualTo(expected);
        }

        @Test
        void inSteady_shouldHold_whenClockOverflowsDuringTheRun() {
            final RunTimeline overflowing = new RunTimeline(PROFILE, () -> Long.MAX_VALUE - 1_000);

            assertThat(overflowing.inSteady(overflowing.steadyStart())).isTrue();
            assertThat(overflowing.inSteady(overflowing.steadyEnd())).isFalse();
        }
    }

    @Nested
    class Validate {
        static Stream<Arguments> invalidProfiles() {
            return Stream.of(
                    invalid("no load bots", builder -> builder.bots(0)),
                    invalid("empty steady", builder -> builder.steady(Duration.ZERO)),
                    invalid("negative ramp", builder -> builder.ramp(Duration.ofSeconds(-1))),
                    invalid("missing warm-up", builder -> builder.warmUp(null)));
        }

        private static Arguments invalid(String name, UnaryOperator<InjectionProfileBuilder> change) {
            return Arguments.of(name, change);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidProfiles")
        void constructor_shouldReject_whenProfileIsInvalid(String name, UnaryOperator<InjectionProfileBuilder> change) {
            final InjectionProfile profile = change.apply(PROFILE.toBuilder()).build();

            assertThatIllegalArgumentException().isThrownBy(() -> new RunTimeline(profile, () -> START));
        }
    }
}
