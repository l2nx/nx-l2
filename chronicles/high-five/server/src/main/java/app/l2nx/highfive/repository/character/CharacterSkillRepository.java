/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterSkillRepository {
    private static final String SELECT_SKILLS =
            "SELECT skill_id, skill_level FROM character_skills WHERE character_id = ? AND class_index = ?";
    private static final String UPDATE_SKILL_LEVEL = """
        UPDATE character_skills SET skill_level = ?
        WHERE character_id = ? AND class_index = ? AND skill_id = ?\
        """;
    private static final String UPSERT_SKILL = """
        INSERT INTO character_skills (character_id, class_index, skill_id, skill_level)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (character_id, class_index, skill_id) DO UPDATE SET skill_level = EXCLUDED.skill_level\
        """;
    private static final String DELETE_SKILL =
            "DELETE FROM character_skills WHERE character_id = ? AND class_index = ? AND skill_id = ?";
    private static final String DELETE_CLASS_SKILLS =
            "DELETE FROM character_skills WHERE character_id = ? AND class_index = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<CharacterSkill> findAll(int objectId, int classIndex) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_SKILLS)
                        .param(id)
                        .param(classIndex)
                        .query((rs, rowNum) -> CharacterSkill.builder()
                                .skillId(rs.getInt("skill_id"))
                                .skillLevel(rs.getInt("skill_level"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    public void updateLevel(int objectId, int classIndex, int skillId, int skillLevel) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_SKILL_LEVEL)
                        .param(skillLevel)
                        .param(id)
                        .param(classIndex)
                        .param(skillId)
                        .update());
    }

    public void save(int objectId, int classIndex, CharacterSkill skill) {
        jdbcClient
                .sql(UPSERT_SKILL)
                .param(References.required(registry, objectId))
                .param(classIndex)
                .param(skill.skillId())
                .param(skill.skillLevel())
                .update();
    }

    public void saveAll(int objectId, int classIndex, List<CharacterSkill> skills) {
        final UUID id = References.required(registry, objectId);
        jdbcTemplate.batchUpdate(
                UPSERT_SKILL,
                skills.stream()
                        .map(skill -> new Object[] {id, classIndex, skill.skillId(), skill.skillLevel()})
                        .toList());
    }

    public void delete(int objectId, int classIndex, int skillId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_SKILL)
                        .param(id)
                        .param(classIndex)
                        .param(skillId)
                        .update());
    }

    public void deleteAll(int objectId, int classIndex) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_CLASS_SKILLS)
                        .param(id)
                        .param(classIndex)
                        .update());
    }
}
