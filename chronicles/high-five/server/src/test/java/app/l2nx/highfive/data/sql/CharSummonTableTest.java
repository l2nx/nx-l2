/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.mechanics.skill.StoredEffect;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CharSummonTableTest {
    private static final int OWNER = 268_480_001;

    @Nested
    class ToStoredEffect {
        @Test
        void toStoredEffect_shouldKeepRemainingAndTickPhase() {
            final Optional<StoredEffect> actual = CharSummonTable.toStoredEffect(row(52_700L, 7_300L), OWNER);

            assertThat(actual)
                    .contains(StoredEffect.builder()
                            .remainingMillis(52_700L)
                            .tickPhaseMillis(7_300)
                            .build());
        }

        @Test
        void toStoredEffect_shouldHaveNoRemaining_whenEffectHasNoDuration() {
            final Optional<StoredEffect> actual = CharSummonTable.toStoredEffect(row(null, 4_000L), OWNER);

            assertThat(actual)
                    .contains(StoredEffect.builder().tickPhaseMillis(4_000).build());
        }

        @ParameterizedTest(name = "remainingMillis={0}, tickPhaseMillis={1}")
        @CsvSource({"0, 0", "-1, 0", "1000, -1"})
        void toStoredEffect_shouldSkipRow_whenItBreaksTheInvariants(long remainingMillis, long tickPhaseMillis) {
            final Optional<StoredEffect> actual =
                    CharSummonTable.toStoredEffect(row(remainingMillis, tickPhaseMillis), OWNER);

            assertThat(actual).isEmpty();
        }
    }

    private static SummonEffectRow row(Long remainingMillis, long tickPhaseMillis) {
        return SummonEffectRow.builder()
                .skillId(1204)
                .skillLevel(2)
                .remainingMillis(remainingMillis)
                .tickPhaseMillis(tickPhaseMillis)
                .build();
    }
}
