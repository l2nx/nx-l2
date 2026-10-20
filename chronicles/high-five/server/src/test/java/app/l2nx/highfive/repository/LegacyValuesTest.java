/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyValuesTest {
    private static final Instant INSTANT = Instant.parse("2027-01-15T08:00:00.123Z");

    @Nested
    class InstantOrNull {
        @ParameterizedTest(name = "{0}")
        @ValueSource(longs = {0, -1})
        void instantOrNull_shouldBeNull_whenNotSet(long epochMillis) {
            assertThat(LegacyValues.instantOrNull(epochMillis)).isNull();
        }

        @Test
        void instantOrNull_shouldKeepMilliseconds_whenSet() {
            assertThat(LegacyValues.instantOrNull(INSTANT.toEpochMilli())).isEqualTo(INSTANT);
        }
    }

    @Nested
    class EpochMillisOrZero {
        @Test
        void epochMillisOrZero_shouldBeZero_whenNull() {
            assertThat(LegacyValues.epochMillisOrZero(null)).isZero();
        }

        @Test
        void epochMillisOrZero_shouldReturnEpochMillis_whenSet() {
            assertThat(LegacyValues.epochMillisOrZero(INSTANT)).isEqualTo(INSTANT.toEpochMilli());
        }
    }
}
