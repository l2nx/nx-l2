/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.mechanics.skill.StoredEffect;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class StoredEffectRows {
    private StoredEffectRows() {}

    /**
     * Skips with a warning a stored row that breaks the {@link StoredEffect} invariants, so one corrupt row does not
     * abort the restore of the owner's other effects.
     * @param remainingMillis {@code null} for an effect without a time limit
     * @param ownerId the character id, or the control item id of a pet
     */
    public static Optional<StoredEffect> read(Long remainingMillis, long tickPhaseMillis, int ownerId, int skillId) {
        try {
            return Optional.of(StoredEffect.builder()
                    .remainingMillis(remainingMillis)
                    .tickPhaseMillis(tickPhaseMillis)
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped stored effect of owner {} skill {}: {}", ownerId, skillId, e.getMessage());
            return Optional.empty();
        }
    }
}
