/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.engine.core.loop.EngineProperties.EnginePropertiesBuilder;
import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnginePropertiesTest {
    @Nested
    class Constructor {
        @ParameterizedTest(name = "{0} = {1}")
        @MethodSource("valuesTheLoopCannotRunWith")
        void constructor_shouldReject_whenTheLoopCannotRunWithTheValue(
                String key, String value, UnaryOperator<EnginePropertiesBuilder> change) {
            final EnginePropertiesBuilder builder = change.apply(EngineProperties.defaults().toBuilder());

            assertThatIllegalArgumentException().isThrownBy(builder::build).withMessageContaining(key);
        }

        static Stream<Arguments> valuesTheLoopCannotRunWith() {
            return Stream.of(
                    rejected("l2nx.engine.trigger-depth", "0", builder -> builder.triggerDepth(0)),
                    rejected("l2nx.engine.pass-min", "500ns", builder -> builder.passMin(Duration.ofNanos(500))),
                    rejected("l2nx.engine.pass-min", "1500us", builder -> builder.passMin(Duration.ofNanos(1_500_000))),
                    rejected("l2nx.engine.pass-budget", "0", builder -> builder.passBudget(Duration.ZERO)),
                    rejected(
                            "l2nx.engine.blocked-pass-warning",
                            "0",
                            builder -> builder.blockedPassWarning(Duration.ZERO)),
                    rejected(
                            "l2nx.engine.blocked-pass-exit",
                            "2s",
                            builder -> builder.blockedPassExit(Duration.ofSeconds(2))),
                    rejected(
                            "l2nx.engine.blocked-pass-exit",
                            "1500us",
                            builder -> builder.blockedPassExit(Duration.ofNanos(1_500_000))));
        }

        private static Arguments rejected(String key, String value, UnaryOperator<EnginePropertiesBuilder> change) {
            return Arguments.of(key, value, change);
        }
    }
}
