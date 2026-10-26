/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.ai.system;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AiSystemPropertiesTest {
    @Nested
    class Constructor {
        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("app.l2nx.highfive.ai.system.AiSystemPropertiesTest#invalidCadences")
        void constructor_shouldReject_whenTheThinkCadenceIsNotWholeMillisecondsOfAtLeastOne(Duration cadence) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AiSystemProperties(cadence))
                    .withMessageContaining("l2nx.ai.think-cadence");
        }
    }

    static Stream<Duration> invalidCadences() {
        return Stream.of(null, Duration.ZERO, Duration.ofNanos(500_000), Duration.ofNanos(1_500_000));
    }
}
