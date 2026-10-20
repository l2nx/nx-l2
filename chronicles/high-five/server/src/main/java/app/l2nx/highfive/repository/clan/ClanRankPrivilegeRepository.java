/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.clan.model.RankPrivilege;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanRankPrivilegeRepository {
    private static final String SELECT_PRIVILEGES =
            "SELECT power_grade, privilege_mask FROM clan_rank_privileges WHERE clan_id = ?";
    private static final String UPSERT_PRIVILEGES = """
        INSERT INTO clan_rank_privileges (clan_id, power_grade, privilege_mask)
        VALUES (?, ?, ?)
        ON CONFLICT (clan_id, power_grade) DO UPDATE SET privilege_mask = EXCLUDED.privilege_mask\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<RankPrivilege> findAll(int clanId) {
        return registry.findId(clanId)
                .map(id -> jdbcClient
                        .sql(SELECT_PRIVILEGES)
                        .param(id)
                        .query((rs, rowNum) -> RankPrivilege.builder()
                                .powerGrade(rs.getInt("power_grade"))
                                .privilegeMask(rs.getInt("privilege_mask"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    /**
     * @param powerGrade 1 to 9; the schema rejects any other rank
     */
    public void upsert(int clanId, int powerGrade, int privilegeMask) {
        jdbcClient
                .sql(UPSERT_PRIVILEGES)
                .param(References.required(registry, clanId))
                .param(powerGrade)
                .param(privilegeMask)
                .update();
    }
}
