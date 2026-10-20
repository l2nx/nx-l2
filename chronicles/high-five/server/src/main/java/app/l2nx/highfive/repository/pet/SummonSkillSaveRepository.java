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

@Repository
@RequiredArgsConstructor
public class SummonSkillSaveRepository {
    private static final String SELECT_EFFECTS = """
        SELECT skill_id, skill_level, remaining_ms, tick_phase_ms
        FROM character_summon_skill_saves
        WHERE character_id = ? AND class_index = ? AND summon_skill_id = ?
        ORDER BY buff_index\
        """;
    // Upsert: two effects of one skill level in a save keep the last.
    private static final String UPSERT_EFFECT = """
        INSERT INTO character_summon_skill_saves (
            character_id, class_index, summon_skill_id, skill_id, skill_level, remaining_ms, tick_phase_ms, buff_index)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (character_id, class_index, summon_skill_id, skill_id, skill_level) DO UPDATE
        SET remaining_ms = EXCLUDED.remaining_ms, tick_phase_ms = EXCLUDED.tick_phase_ms,
            buff_index = EXCLUDED.buff_index\
        """;
    private static final String DELETE_EFFECTS = """
        DELETE FROM character_summon_skill_saves
        WHERE character_id = ? AND class_index = ? AND summon_skill_id = ?\
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<SummonEffectRow> findAll(int ownerId, int classIndex, int summonSkillId) {
        return registry.findId(ownerId)
                .map(id -> jdbcClient
                        .sql(SELECT_EFFECTS)
                        .param(id)
                        .param(classIndex)
                        .param(summonSkillId)
                        .query((rs, rowNum) -> SummonEffectRow.builder()
                                .skillId(rs.getInt("skill_id"))
                                .skillLevel(rs.getInt("skill_level"))
                                .remainingMillis(rs.getObject("remaining_ms", Long.class))
                                .tickPhaseMillis(rs.getLong("tick_phase_ms"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    @Transactional
    public void replaceAll(int ownerId, int classIndex, int summonSkillId, List<SummonEffectRow> effects) {
        final UUID characterId = References.required(registry, ownerId);
        jdbcClient
                .sql(DELETE_EFFECTS)
                .param(characterId)
                .param(classIndex)
                .param(summonSkillId)
                .update();
        final List<Object[]> rows = new ArrayList<>(effects.size());
        for (SummonEffectRow effect : effects) {
            rows.add(new Object[] {
                characterId,
                classIndex,
                summonSkillId,
                effect.skillId(),
                effect.skillLevel(),
                effect.remainingMillis(),
                effect.tickPhaseMillis(),
                rows.size() + 1
            });
        }

        jdbcTemplate.batchUpdate(UPSERT_EFFECT, rows);
    }

    public void deleteAll(int ownerId, int classIndex, int summonSkillId) {
        registry.findId(ownerId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_EFFECTS)
                        .param(id)
                        .param(classIndex)
                        .param(summonSkillId)
                        .update());
    }
}
