/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InjectionProfileTest {
    private static final InjectionProfile.Levels LEVELS = InjectionProfile.Levels.builder()
            .increment(250)
            .count(7)
            .duration(Duration.ofSeconds(90))
            .ramp(Duration.ofSeconds(30))
            .build();

    @Nested
    class TotalBots {
        @Test
        void totalBots_shouldAddEveryIncrementToTheFirstLevel_whenTheRunHasLevels() {
            var actual = InjectionProfile.builder().bots(500).levels(LEVELS).build();

            assertThat(actual.totalBots()).isEqualTo(2_000);
        }

        @Test
        void totalBots_shouldBeTheBots_whenTheRunHasNoLevels() {
            assertThat(InjectionProfile.builder().bots(500).build().totalBots()).isEqualTo(500);
        }
    }

    @Nested
    class Levels {
        static Stream<Arguments> invalidLevels() {
            return Stream.of(
                    Arguments.of(
                            "no increment", (UnaryOperator<InjectionProfile.Levels.LevelsBuilder>) b -> b.increment(0)),
                    Arguments.of("no count", (UnaryOperator<InjectionProfile.Levels.LevelsBuilder>) b -> b.count(0)),
                    Arguments.of("zero duration", (UnaryOperator<InjectionProfile.Levels.LevelsBuilder>)
                            b -> b.duration(Duration.ZERO)),
                    Arguments.of("negative ramp", (UnaryOperator<InjectionProfile.Levels.LevelsBuilder>)
                            b -> b.ramp(Duration.ofSeconds(-1))),
                    Arguments.of("no ramp", (UnaryOperator<InjectionProfile.Levels.LevelsBuilder>) b -> b.ramp(null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidLevels")
        void construct_shouldThrow_whenALevelSettingIsOutOfRange(
                String name, UnaryOperator<InjectionProfile.Levels.LevelsBuilder> change) {
            var builder = change.apply(LEVELS.toBuilder());

            assertThatIllegalArgumentException().isThrownBy(builder::build);
        }
    }
}
