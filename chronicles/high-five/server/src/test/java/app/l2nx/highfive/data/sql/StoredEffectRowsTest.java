/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.mechanics.skill.StoredEffect;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoredEffectRowsTest {
    private static final int OWNER_ID = 268_480_001;
    private static final int SKILL_ID = 1204;

    @Nested
    class Read {
        @Test
        void read_shouldKeepRemainingAndTickPhase() {
            final Optional<StoredEffect> actual = StoredEffectRows.read(52_700L, 7_300L, OWNER_ID, SKILL_ID);

            assertThat(actual)
                    .contains(StoredEffect.builder()
                            .remainingMillis(52_700L)
                            .tickPhaseMillis(7_300)
                            .build());
        }

        @Test
        void read_shouldHaveNoRemaining_whenRemainingIsNull() {
            final Optional<StoredEffect> actual = StoredEffectRows.read(null, 4_000L, OWNER_ID, SKILL_ID);

            assertThat(actual)
                    .contains(StoredEffect.builder().tickPhaseMillis(4_000).build());
        }

        @ParameterizedTest(name = "remaining_ms={0}, tick_phase_ms={1}")
        @CsvSource({"0, 0", "-1, 0", "1000, -1"})
        void read_shouldSkipTheRow_whenItBreaksTheInvariants(long remainingMillis, long tickPhaseMillis) {
            final Optional<StoredEffect> actual =
                    StoredEffectRows.read(remainingMillis, tickPhaseMillis, OWNER_ID, SKILL_ID);

            assertThat(actual).isEmpty();
        }
    }
}
