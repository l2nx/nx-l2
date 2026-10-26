/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DurationsTest {
    @Nested
    class RequireWholeMillis {
        @ParameterizedTest(name = "{0}")
        @MethodSource("notWholeMillis")
        void requireWholeMillis_shouldRejectNamingTheProperty_whenTheValueIsNotWholeMillisOfAtLeastOne(Duration value) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Durations.requireWholeMillis("l2nx.test.cadence", value))
                    .withMessageStartingWith("l2nx.test.cadence must be a whole number of milliseconds");
        }

        @Test
        void requireWholeMillis_shouldAccept_whenTheValueIsWholeMillis() {
            assertThatCode(() -> Durations.requireWholeMillis("l2nx.test.cadence", Duration.ofSeconds(1, 1_000_000)))
                    .doesNotThrowAnyException();
        }

        static Stream<Duration> notWholeMillis() {
            return Stream.of(null, Duration.ZERO, Duration.ofNanos(500_000), Duration.ofNanos(1_500_000));
        }
    }
}
