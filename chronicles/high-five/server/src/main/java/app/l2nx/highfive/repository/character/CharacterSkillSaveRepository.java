/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterSkillSave;
import app.l2nx.highfive.repository.character.model.SkillRestoreType;
import java.time.Instant;
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
public class CharacterSkillSaveRepository {
    private static final String SELECT_SAVES = """
        SELECT skill_id, skill_level, restore_type, remaining_ms, tick_phase_ms, reuse_delay_ms, reuse_ends_at
        FROM character_skill_saves
        WHERE character_id = ? AND class_index = ?
        ORDER BY buff_order\
        """;
    private static final String UPSERT_SAVE = """
        INSERT INTO character_skill_saves (
            character_id, class_index, skill_id, skill_level, restore_type, remaining_ms, tick_phase_ms,
            reuse_delay_ms, reuse_ends_at, buff_order)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (character_id, class_index, skill_id, skill_level) DO UPDATE
        SET restore_type = EXCLUDED.restore_type, remaining_ms = EXCLUDED.remaining_ms,
            tick_phase_ms = EXCLUDED.tick_phase_ms, reuse_delay_ms = EXCLUDED.reuse_delay_ms,
            reuse_ends_at = EXCLUDED.reuse_ends_at, buff_order = EXCLUDED.buff_order\
        """;
    private static final String DELETE_CLASS_SAVES =
            "DELETE FROM character_skill_saves WHERE character_id = ? AND class_index = ?";
    private static final String DELETE_SKILL_OF_OFFLINE = """
        DELETE FROM character_skill_saves s
        USING characters c
        WHERE c.id = s.character_id AND c.online_status = 'OFFLINE' AND s.skill_id = ?\
        """;
    private static final String DELETE_EXPIRED_REUSE =
            "DELETE FROM character_skill_saves WHERE restore_type = 'REUSE' AND reuse_ends_at <= ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<CharacterSkillSave> findAll(int objectId, int classIndex) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_SAVES)
                        .param(id)
                        .param(classIndex)
                        .query((rs, rowNum) -> CharacterSkillSave.builder()
                                .skillId(rs.getInt("skill_id"))
                                .skillLevel(rs.getInt("skill_level"))
                                .restoreType(SkillRestoreType.valueOf(rs.getString("restore_type")))
                                .remainingMs(rs.getObject("remaining_ms", Long.class))
                                .tickPhaseMs(rs.getObject("tick_phase_ms", Long.class))
                                .reuseDelayMs(rs.getLong("reuse_delay_ms"))
                                .reuseEndsAt(JdbcTimes.instant(rs, "reuse_ends_at"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    @Transactional
    public void replaceAll(int objectId, int classIndex, List<CharacterSkillSave> saves) {
        final UUID id = References.required(registry, objectId);
        deleteAll(objectId, classIndex);
        final List<Object[]> rows = new ArrayList<>(saves.size());
        for (CharacterSkillSave save : saves) {
            rows.add(new Object[] {
                id,
                classIndex,
                save.skillId(),
                save.skillLevel(),
                save.restoreType().name(),
                save.remainingMs(),
                save.tickPhaseMs(),
                save.reuseDelayMs(),
                JdbcTimes.utc(save.reuseEndsAt()),
                rows.size() + 1
            });
        }

        jdbcTemplate.batchUpdate(UPSERT_SAVE, rows);
    }

    public void deleteAll(int objectId, int classIndex) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_CLASS_SAVES)
                        .param(id)
                        .param(classIndex)
                        .update());
    }

    /**
     * Drops a skill from the saves of every offline character; online characters hold theirs in memory.
     */
    public void deleteSkillOfOfflineCharacters(int skillId) {
        jdbcClient.sql(DELETE_SKILL_OF_OFFLINE).param(skillId).update();
    }

    public void deleteExpiredReuse(Instant now) {
        jdbcClient.sql(DELETE_EXPIRED_REUSE).param(JdbcTimes.utc(now)).update();
    }
}
