/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GateThresholdsTest {

    @Nested
    class Construct {

        static Stream<Arguments> invalidThresholds() {
            return Stream.of(
                    Arguments.of("runs 0", (UnaryOperator<GateThresholds.GateThresholdsBuilder>) b -> b.runs(0)),
                    Arguments.of("zero pass budget", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.passBudget(Duration.ZERO)),
                    Arguments.of("no pass budget", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.passBudget(null)),
                    Arguments.of("negative over-budget share", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.maxOverBudgetShare(-0.1)),
                    Arguments.of("timeout share above 1", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.maxTimeoutAndErrorShare(1.5)),
                    Arguments.of("NaN share", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.maxOverBudgetShare(Double.NaN)),
                    Arguments.of("zero input ratio", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.maxInputP99Ratio(0)),
                    Arguments.of("no input floor", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.inputP99Floor(null)),
                    Arguments.of("negative input floor", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.inputP99Floor(Duration.ofMillis(-1))),
                    Arguments.of("zero step ratio", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.maxStepP99Ratio(0)),
                    Arguments.of("no step floor", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.stepP99Floor(null)),
                    Arguments.of("negative step floor", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.stepP99Floor(Duration.ofMillis(-1))),
                    Arguments.of("not-at-final share above 1", (UnaryOperator<GateThresholds.GateThresholdsBuilder>)
                            b -> b.maxNotAtFinalStateShare(1.5)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidThresholds")
        void construct_shouldThrow_whenThresholdIsOutOfRange(
                String name, UnaryOperator<GateThresholds.GateThresholdsBuilder> change) {
            var builder = change.apply(GateThresholds.defaults().toBuilder());

            assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Defaults {
        @Test
        void defaults_shouldFollowSpec009() {
            var actual = GateThresholds.defaults();

            assertThat(actual.runs())
                    .as("the median survives one outlier on each side")
                    .isEqualTo(5);
            assertThat(actual.maxInputP99Ratio()).isEqualTo(1.10);
            assertThat(actual.inputP99Floor()).isEqualTo(Duration.ofMillis(1));
            assertThat(actual.maxStepP99Ratio()).isEqualTo(1.10);
            assertThat(actual.stepP99Floor()).isEqualTo(Duration.ofMillis(10));
            assertThat(actual.passBudget()).isEqualTo(Duration.ofMillis(50));
        }
    }
}
