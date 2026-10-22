/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.load.injection.InjectionProfile.InjectionProfileBuilder;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

    private final RunTimeline timeline = new RunTimeline(PROFILE, MeasuredPhase.STEADY, () -> START);

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
    class Measured {
        @ParameterizedTest(name = "{0} at {1} ms measured: {2}")
        @CsvSource({
            "STEADY, 2999, false",
            "STEADY, 3000, true",
            "STEADY, 12999, true",
            "STEADY, 13000, false",
            "RAMP, -1, false",
            "RAMP, 0, true",
            "RAMP, 999, true",
            "RAMP, 1000, false"
        })
        void inMeasured_shouldIncludeStartAndExcludeEnd(MeasuredPhase phase, long millis, boolean expected) {
            final RunTimeline actual = new RunTimeline(PROFILE, phase, () -> START);

            assertThat(actual.inMeasured(at(millis))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} spans [{1}, {2}) ms")
        @CsvSource({"STEADY, 3000, 13000", "RAMP, 0, 1000"})
        void measuredBounds_shouldFollowThePhase(MeasuredPhase phase, long startMillis, long endMillis) {
            final RunTimeline actual = new RunTimeline(PROFILE, phase, () -> START);

            assertThat(actual.measuredStart()).isEqualTo(at(startMillis));
            assertThat(actual.measuredEnd()).isEqualTo(at(endMillis));
        }

        @Test
        void constructor_shouldAcceptAnEmptySteady_whenMeasuringTheRamp() {
            final InjectionProfile profile = PROFILE.toBuilder()
                    .warmUp(Duration.ZERO)
                    .steady(Duration.ZERO)
                    .rampDown(Duration.ZERO)
                    .build();

            final RunTimeline actual = new RunTimeline(profile, MeasuredPhase.RAMP, () -> START);

            assertThat(actual.measuredEnd()).isEqualTo(at(1000));
            assertThat(actual.end()).isEqualTo(at(1000));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(MeasuredPhase.class)
        void inMeasured_shouldHold_whenClockOverflowsDuringTheRun(MeasuredPhase phase) {
            final RunTimeline overflowing = new RunTimeline(PROFILE, phase, () -> Long.MAX_VALUE - 1_000);

            assertThat(overflowing.inMeasured(overflowing.measuredStart())).isTrue();
            assertThat(overflowing.inMeasured(overflowing.measuredEnd())).isFalse();
        }
    }

    @Nested
    class Counts {
        @ParameterizedTest(name = "{0}: load bot {1}, sample at {2} ms counts: {3}")
        @CsvSource({
            "STEADY, 0, 2999, false",
            "STEADY, 0, 3000, true",
            "STEADY, 3, 12999, true",
            "STEADY, 3, 13000, false",
            "RAMP, 0, 0, true",
            "RAMP, 3, 750, true",
            "RAMP, 3, 5000, true"
        })
        void countsArrival_shouldTakeTheSampleTimeInSteadyAndTheIntendedStartInTheRamp(
                MeasuredPhase phase, int index, long millis, boolean expected) {
            final RunTimeline actual = new RunTimeline(PROFILE, phase, () -> START);

            assertThat(actual.countsArrival(index, at(millis))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}: action at {1} ms counts: {2}")
        @CsvSource({
            "STEADY, 2999, false",
            "STEADY, 3000, true",
            "STEADY, 13000, false",
            "RAMP, 0, false",
            "RAMP, 500, false"
        })
        void countsAction_shouldCountOnlyActionsOfTheSteadyWindow(MeasuredPhase phase, long millis, boolean expected) {
            final RunTimeline actual = new RunTimeline(PROFILE, phase, () -> START);

            assertThat(actual.countsAction(at(millis))).isEqualTo(expected);
        }

        @Test
        void countsLag_shouldFollowTheSteadyWindow_whenItIsMeasured() {
            assertThat(timeline.countsLag(at(2999))).isFalse();
            assertThat(timeline.countsLag(at(3000))).isTrue();
            assertThat(timeline.countsLag(at(13000))).isFalse();
        }

        @Test
        void countsLag_shouldHoldInTheRampUntilEveryLoadBotIsDoneArriving() {
            final RunTimeline actual = new RunTimeline(PROFILE, MeasuredPhase.RAMP, () -> START);
            for (int index = 0; index < PROFILE.bots() - 1; index++) {
                actual.arrivalOver();
            }

            assertThat(actual.countsLag(at(5000)))
                    .as("one load bot still arriving")
                    .isTrue();

            actual.arrivalOver();

            assertThat(actual.countsLag(at(500))).isFalse();
        }
    }

    @Nested
    class AwaitMeasuredEnd {
        private final AtomicLong clock = new AtomicLong(START);

        @Test
        void awaitMeasuredEnd_shouldEndTheRampOnceEveryLoadBotIsDoneArriving() throws InterruptedException {
            final RunTimeline actual = new RunTimeline(PROFILE, MeasuredPhase.RAMP, clock::get);
            clock.set(at(5000));
            for (int index = 0; index < PROFILE.bots() - 1; index++) {
                actual.arrivalOver();
            }

            assertThat(actual.awaitMeasuredEnd(at(5000)))
                    .as("past the ramp, one still arriving")
                    .isFalse();

            actual.arrivalOver();

            assertThat(actual.awaitMeasuredEnd(at(5000))).isTrue();
        }

        @ParameterizedTest(name = "now and until {0} ms -> over {1}")
        @CsvSource({"12999, false", "13000, true", "14000, true"})
        void awaitMeasuredEnd_shouldEndTheSteadyWindowAtItsEnd(long millis, boolean expected)
                throws InterruptedException {
            final RunTimeline actual = new RunTimeline(PROFILE, MeasuredPhase.STEADY, clock::get);
            clock.set(at(millis));

            assertThat(actual.awaitMeasuredEnd(at(millis))).isEqualTo(expected);
        }
    }

    @Nested
    class Validate {
        static Stream<Arguments> invalidProfiles() {
            return Stream.of(
                    invalid("no load bots", MeasuredPhase.STEADY, builder -> builder.bots(0), "at least one load bot"),
                    invalid(
                            "empty steady",
                            MeasuredPhase.STEADY,
                            builder -> builder.steady(Duration.ZERO),
                            "needs a positive steady"),
                    invalid(
                            "empty ramp",
                            MeasuredPhase.RAMP,
                            builder -> builder.ramp(Duration.ZERO),
                            "needs a positive ramp"),
                    invalid(
                            "negative ramp",
                            MeasuredPhase.STEADY,
                            builder -> builder.ramp(Duration.ofSeconds(-1)),
                            "ramp must be zero or positive"),
                    invalid(
                            "missing warm-up",
                            MeasuredPhase.RAMP,
                            builder -> builder.warmUp(null),
                            "warm-up must be zero or positive"),
                    invalid("no measured phase", null, builder -> builder, "measured phase is required"));
        }

        private static Arguments invalid(
                String name, MeasuredPhase phase, UnaryOperator<InjectionProfileBuilder> change, String message) {
            return Arguments.of(name, phase, change, message);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidProfiles")
        void constructor_shouldReject_whenProfileIsInvalid(
                String name, MeasuredPhase phase, UnaryOperator<InjectionProfileBuilder> change, String message) {
            final InjectionProfile profile = change.apply(PROFILE.toBuilder()).build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RunTimeline(profile, phase, () -> START))
                    .withMessageContaining(message);
        }
    }
}
