/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.siege.model.FlagWarAttacker;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FlagWarAttackerRepository {
    private static final String SELECT_BY_HALL = """
        SELECT clan_id, flag_npc_id, ally_npc_id
        FROM siegable_hall_flag_war_attackers
        WHERE clan_hall_id = ?
        ORDER BY flag_npc_id\
        """;
    private static final String SELECT_MEMBERS = """
        SELECT character_id
        FROM siegable_hall_flag_war_attacker_members
        WHERE clan_hall_id = ? AND clan_id = ?
        ORDER BY character_id\
        """;
    private static final String INSERT = """
        INSERT INTO siegable_hall_flag_war_attackers (clan_hall_id, clan_id, flag_npc_id) VALUES (?, ?, ?)\
        """;
    private static final String UPDATE_ALLY_NPC = """
        UPDATE siegable_hall_flag_war_attackers SET ally_npc_id = ? WHERE clan_hall_id = ? AND clan_id = ?\
        """;
    // The same player listed twice would take two of the 18 member slots.
    private static final String INSERT_MEMBER = """
        INSERT INTO siegable_hall_flag_war_attacker_members (clan_hall_id, clan_id, character_id) VALUES (?, ?, ?)
        ON CONFLICT DO NOTHING\
        """;
    private static final String DELETE_BY_HALL = "DELETE FROM siegable_hall_flag_war_attackers WHERE clan_hall_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<FlagWarAttacker> findByHall(int clanHallId) {
        return jdbcClient
                .sql(SELECT_BY_HALL)
                .param(clanHallId)
                .query((rs, rowNum) -> FlagWarAttacker.builder()
                        .clanId(registry.handleOf(rs.getObject("clan_id", UUID.class)))
                        .flagNpcId(rs.getInt("flag_npc_id"))
                        .allyNpcId(rs.getInt("ally_npc_id"))
                        .build())
                .list();
    }

    public List<Integer> findMemberIds(int clanHallId, int clanId) {
        return registry.findId(clanId)
                .map(id -> jdbcClient
                        .sql(SELECT_MEMBERS)
                        .param(clanHallId)
                        .param(id)
                        .query((rs, rowNum) -> registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .list())
                .orElseGet(List::of);
    }

    public void insert(int clanHallId, int clanId, int flagNpcId) {
        jdbcClient
                .sql(INSERT)
                .param(clanHallId)
                .param(References.required(registry, clanId))
                .param(flagNpcId)
                .update();
    }

    /**
     * @param allyNpcId {@code 0} clears the choice
     */
    public void updateAllyNpc(int clanHallId, int clanId, int allyNpcId) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_ALLY_NPC)
                        .param(allyNpcId == 0 ? null : allyNpcId)
                        .param(clanHallId)
                        .param(id)
                        .update());
    }

    public void insertMember(int clanHallId, int clanId, int characterId) {
        jdbcClient
                .sql(INSERT_MEMBER)
                .param(clanHallId)
                .param(References.required(registry, clanId))
                .param(References.required(registry, characterId))
                .update();
    }

    public void deleteByHall(int clanHallId) {
        jdbcClient.sql(DELETE_BY_HALL).param(clanHallId).update();
    }
}
