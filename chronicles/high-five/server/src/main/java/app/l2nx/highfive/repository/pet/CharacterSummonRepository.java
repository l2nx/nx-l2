/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.pet;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.pet.model.CharacterSummon;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterSummonRepository {
    private static final String SELECT_SUMMON_SKILLS = "SELECT character_id, summon_skill_id FROM character_summons";
    private static final String SELECT_SUMMON = """
        SELECT character_id, summon_skill_id, current_hp, current_mp, life_remaining_ms
        FROM character_summons
        WHERE character_id = ? AND summon_skill_id = ?\
        """;
    private static final String UPSERT_SUMMON = """
        INSERT INTO character_summons (character_id, summon_skill_id, current_hp, current_mp, life_remaining_ms)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (character_id, summon_skill_id) DO UPDATE
        SET current_hp = EXCLUDED.current_hp, current_mp = EXCLUDED.current_mp,
            life_remaining_ms = EXCLUDED.life_remaining_ms\
        """;
    private static final String DELETE_SUMMONS = "DELETE FROM character_summons WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Map<Integer, Integer> findSummonSkills() {
        final Map<Integer, Integer> summons = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_SUMMON_SKILLS).query((RowCallbackHandler) rs ->
                summons.put(registry.handleOf(rs.getObject("character_id", UUID.class)), rs.getInt("summon_skill_id")));
        return summons;
    }

    public Optional<CharacterSummon> find(int ownerId, int summonSkillId) {
        return registry.findId(ownerId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_SUMMON)
                        .param(id)
                        .param(summonSkillId)
                        .query((rs, rowNum) -> CharacterSummon.builder()
                                .ownerId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                                .summonSkillId(rs.getInt("summon_skill_id"))
                                .currentHp(rs.getInt("current_hp"))
                                .currentMp(rs.getInt("current_mp"))
                                .lifeRemainingMs(rs.getLong("life_remaining_ms"))
                                .build())
                        .optional());
    }

    public void save(CharacterSummon summon) {
        jdbcClient
                .sql(UPSERT_SUMMON)
                .param(References.required(registry, summon.ownerId()))
                .param(summon.summonSkillId())
                .param(summon.currentHp())
                .param(summon.currentMp())
                .param(summon.lifeRemainingMs())
                .update();
    }

    public void deleteByOwner(int ownerId) {
        registry.findId(ownerId)
                .ifPresent(id -> jdbcClient.sql(DELETE_SUMMONS).param(id).update());
    }
}
