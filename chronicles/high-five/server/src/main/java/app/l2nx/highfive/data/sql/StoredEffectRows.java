/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.mechanics.skill.StoredEffect;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class StoredEffectRows {
    private StoredEffectRows() {}

    /**
     * Skips with a warning a row that breaks the {@link StoredEffect} invariants, so one corrupt row does not abort
     * the restore of the owner's other effects.
     * @param ownerId the character id, or the control item id of a pet
     */
    public static Optional<StoredEffect> read(ResultSet rs, int ownerId) throws SQLException {
        try {
            return Optional.of(StoredEffect.builder()
                    .remainingMillis(rs.getObject("remaining_ms", Long.class))
                    .tickPhaseMillis(rs.getLong("tick_phase_ms"))
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped stored effect of owner {} skill {}: {}", ownerId, rs.getInt("skill_id"), e.getMessage());
            return Optional.empty();
        }
    }
}
