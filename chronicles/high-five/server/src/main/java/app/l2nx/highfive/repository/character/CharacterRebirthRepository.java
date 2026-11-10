/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterRebirthRepository {
    private static final String SELECT_REBIRTH_COUNT =
            "SELECT rebirth_count FROM character_rebirths WHERE character_id = ?";
    private static final String UPSERT_REBIRTH_COUNT = """
        INSERT INTO character_rebirths (character_id, rebirth_count)
        VALUES (?, ?)
        ON CONFLICT (character_id) DO UPDATE SET rebirth_count = EXCLUDED.rebirth_count\
        """;
    private static final String SELECT_SELECTED_SKILLS =
            "SELECT selected_skill_ids FROM character_rebirths WHERE character_id = ?";
    private static final String UPDATE_SELECTED_SKILLS =
            "UPDATE character_rebirths SET selected_skill_ids = ? WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public int findRebirthCount(int characterId) {
        return registry.findId(characterId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_REBIRTH_COUNT)
                        .param(id)
                        .query(Integer.class)
                        .optional())
                .orElse(0);
    }

    public void saveRebirthCount(int characterId, int rebirthCount) {
        jdbcClient
                .sql(UPSERT_REBIRTH_COUNT)
                .param(References.required(registry, characterId))
                .param(rebirthCount)
                .update();
    }

    public List<Integer> findSelectedSkillIds(UUID characterId) {
        return jdbcClient
                .sql(SELECT_SELECTED_SKILLS)
                .param(characterId)
                .query((rs, rowNum) ->
                        List.of((Integer[]) rs.getArray("selected_skill_ids").getArray()))
                .optional()
                .orElse(List.of());
    }

    public List<Integer> findSelectedSkillIds(int characterId) {
        return registry.findId(characterId).map(this::findSelectedSkillIds).orElse(List.of());
    }

    /**
     * Writes nothing for a character never reborn: the selection belongs to a rebirth row.
     */
    public void saveSelectedSkillIds(int characterId, List<Integer> skillIds) {
        jdbcClient
                .sql(UPDATE_SELECTED_SKILLS)
                .param(skillIds.toArray(Integer[]::new))
                .param(References.required(registry, characterId))
                .update();
    }
}
