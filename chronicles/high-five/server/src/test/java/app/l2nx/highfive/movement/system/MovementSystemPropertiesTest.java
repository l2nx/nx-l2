/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.system;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MovementSystemPropertiesTest {
    @Nested
    class Constructor {
        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("app.l2nx.highfive.movement.system.MovementSystemPropertiesTest#invalidCadences")
        void constructor_shouldReject_whenACadenceIsNotWholeMillisecondsOfAtLeastOne(
                String key, UnaryOperator<MovementSystemProperties.MovementSystemPropertiesBuilder> change) {
            final MovementSystemProperties.MovementSystemPropertiesBuilder valid = MovementSystemProperties.builder()
                    .playerCadence(Duration.ofMillis(50))
                    .npcCadence(Duration.ofMillis(100));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> change.apply(valid).build())
                    .withMessageContaining("l2nx.movement." + key);
        }
    }

    static Stream<Arguments> invalidCadences() {
        return Stream.of(
                Arguments.of("player-cadence", change(builder -> builder.playerCadence(null))),
                Arguments.of("player-cadence", change(builder -> builder.playerCadence(Duration.ofNanos(500_000)))),
                Arguments.of("npc-cadence", change(builder -> builder.npcCadence(Duration.ZERO))),
                Arguments.of("npc-cadence", change(builder -> builder.npcCadence(Duration.ofNanos(100_500_000)))));
    }

    private static UnaryOperator<MovementSystemProperties.MovementSystemPropertiesBuilder> change(
            UnaryOperator<MovementSystemProperties.MovementSystemPropertiesBuilder> change) {
        return change;
    }
}
