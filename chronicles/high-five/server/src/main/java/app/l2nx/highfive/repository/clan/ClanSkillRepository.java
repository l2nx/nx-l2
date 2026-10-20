/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.clan.model.ClanSkill;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanSkillRepository {
    /** Legacy pledge type of a skill of the whole clan rather than of one sub-unit; stored as NULL. */
    public static final int CLAN_WIDE = -2;

    private static final String SELECT_SKILLS =
            "SELECT skill_id, skill_level, pledge_type FROM clan_skills WHERE clan_id = ?";
    private static final String UPSERT_SKILL = """
        INSERT INTO clan_skills (clan_id, skill_id, skill_level, pledge_type)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (clan_id, skill_id, pledge_type) DO UPDATE SET skill_level = EXCLUDED.skill_level\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<ClanSkill> findAll(int clanId) {
        return registry.findId(clanId)
                .map(id -> jdbcClient
                        .sql(SELECT_SKILLS)
                        .param(id)
                        .query((rs, rowNum) -> {
                            final Integer pledgeType = rs.getObject("pledge_type", Integer.class);
                            return ClanSkill.builder()
                                    .skillId(rs.getInt("skill_id"))
                                    .skillLevel(rs.getInt("skill_level"))
                                    .pledgeType(pledgeType == null ? CLAN_WIDE : pledgeType)
                                    .build();
                        })
                        .list())
                .orElse(List.of());
    }

    /**
     * @param pledgeType {@link #CLAN_WIDE}, 0 for the main clan squad, else the sub-unit type
     */
    public void upsert(int clanId, int skillId, int skillLevel, int pledgeType) {
        jdbcClient
                .sql(UPSERT_SKILL)
                .param(References.required(registry, clanId))
                .param(skillId)
                .param(skillLevel)
                .param(pledgeType == CLAN_WIDE ? null : pledgeType)
                .update();
    }
}
