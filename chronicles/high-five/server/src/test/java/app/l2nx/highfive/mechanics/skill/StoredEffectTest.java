/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.skill;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StoredEffectTest {
    @Nested
    class Constructor {
        @Test
        void storedEffect_shouldReject_nonPositiveRemaining() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StoredEffect.builder()
                            .remainingMillis(0L)
                            .tickPhaseMillis(0)
                            .build());
        }

        @Test
        void storedEffect_shouldReject_negativeTickPhase() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StoredEffect.builder()
                            .remainingMillis(1_000L)
                            .tickPhaseMillis(-1)
                            .build());
        }
    }
}
