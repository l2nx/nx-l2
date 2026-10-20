/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan;

import static app.l2nx.highfive.repository.LegacyValues.handleOrZero;
import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.clan.model.SubPledgeRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanSubPledgeRepository {
    private static final String SELECT_SUB_PLEDGES =
            "SELECT pledge_type, name, leader_character_id FROM clan_subpledges WHERE clan_id = ?";
    private static final String INSERT_SUB_PLEDGE = """
        INSERT INTO clan_subpledges (clan_id, pledge_type, name, leader_character_id)
        VALUES (?, ?, ?, ?)\
        """;
    private static final String UPDATE_SUB_PLEDGE = """
        UPDATE clan_subpledges
        SET name = ?, leader_character_id = ?
        WHERE clan_id = ? AND pledge_type = ?\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<SubPledgeRow> findAll(int clanId) {
        return registry.findId(clanId)
                .map(id -> jdbcClient
                        .sql(SELECT_SUB_PLEDGES)
                        .param(id)
                        .query((rs, rowNum) -> SubPledgeRow.builder()
                                .pledgeType(rs.getInt("pledge_type"))
                                .name(rs.getString("name"))
                                .leaderId(handleOrZero(registry, rs, "leader_character_id"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    /**
     * @param leaderId 0 for none
     */
    public void insert(int clanId, int pledgeType, String name, int leaderId) {
        jdbcClient
                .sql(INSERT_SUB_PLEDGE)
                .param(References.required(registry, clanId))
                .param(pledgeType)
                .param(name)
                .param(requiredOrNull(registry, leaderId))
                .update();
    }

    /**
     * @param leaderId 0 for none
     */
    public void update(int clanId, int pledgeType, String name, int leaderId) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_SUB_PLEDGE)
                        .param(name)
                        .param(requiredOrNull(registry, leaderId))
                        .param(id)
                        .param(pledgeType)
                        .update());
    }
}
