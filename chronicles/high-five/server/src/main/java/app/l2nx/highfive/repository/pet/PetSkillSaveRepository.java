/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.pet;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Effects of a pet that is not summoned, keyed by its control item: they may be stored before the pet itself.
 */
@Repository
@RequiredArgsConstructor
public class PetSkillSaveRepository {
    private static final String SELECT_EFFECTS = """
        SELECT skill_id, skill_level, remaining_ms, tick_phase_ms
        FROM character_pet_skill_saves
        WHERE item_id = ?
        ORDER BY buff_index\
        """;
    private static final String INSERT_EFFECT = """
        INSERT INTO character_pet_skill_saves (item_id, skill_id, skill_level, remaining_ms, tick_phase_ms, buff_index)
        VALUES (?, ?, ?, ?, ?, ?)\
        """;
    private static final String DELETE_EFFECTS = "DELETE FROM character_pet_skill_saves WHERE item_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    /**
     * @return the effects in the order they were applied
     */
    public List<SummonEffectRow> findAll(int controlItemId) {
        return registry.findId(controlItemId)
                .map(id -> jdbcClient
                        .sql(SELECT_EFFECTS)
                        .param(id)
                        .query((rs, rowNum) -> SummonEffectRow.builder()
                                .skillId(rs.getInt("skill_id"))
                                .skillLevel(rs.getInt("skill_level"))
                                .remainingMillis(rs.getObject("remaining_ms", Long.class))
                                .tickPhaseMillis(rs.getLong("tick_phase_ms"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    /**
     * Replaces the stored effects; the list order becomes the order they are applied in.
     */
    @Transactional
    public void replaceAll(int controlItemId, List<SummonEffectRow> effects) {
        final UUID itemId = References.required(registry, controlItemId);
        jdbcClient.sql(DELETE_EFFECTS).param(itemId).update();
        final List<Object[]> rows = new ArrayList<>(effects.size());
        for (SummonEffectRow effect : effects) {
            rows.add(new Object[] {
                itemId,
                effect.skillId(),
                effect.skillLevel(),
                effect.remainingMillis(),
                effect.tickPhaseMillis(),
                rows.size() + 1
            });
        }

        jdbcTemplate.batchUpdate(INSERT_EFFECT, rows);
    }

    public void deleteAll(int controlItemId) {
        registry.findId(controlItemId)
                .ifPresent(id -> jdbcClient.sql(DELETE_EFFECTS).param(id).update());
    }
}
