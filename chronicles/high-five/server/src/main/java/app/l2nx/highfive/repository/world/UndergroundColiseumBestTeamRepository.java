/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.world.model.UndergroundColiseumBestTeam;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UndergroundColiseumBestTeamRepository {
    private static final String SELECT_ALL = """
        SELECT t.arena_id, t.leader_character_id, c.name, t.wins
        FROM underground_coliseum_best_teams t
        JOIN characters c ON c.id = t.leader_character_id
        ORDER BY t.arena_id\
        """;
    private static final String UPSERT = """
        INSERT INTO underground_coliseum_best_teams (arena_id, leader_character_id, wins)
        VALUES (?, ?, ?)
        ON CONFLICT (arena_id) DO UPDATE
        SET leader_character_id = EXCLUDED.leader_character_id, wins = EXCLUDED.wins\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<UndergroundColiseumBestTeam> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> UndergroundColiseumBestTeam.builder()
                        .arenaId(rs.getInt("arena_id"))
                        .leaderId(registry.handleOf(rs.getObject("leader_character_id", UUID.class)))
                        .leaderName(rs.getString("name"))
                        .wins(rs.getInt("wins"))
                        .build())
                .list();
    }

    /**
     * Stores the arena's best team; {@code team.leaderName()} is ignored.
     */
    public void save(UndergroundColiseumBestTeam team) {
        jdbcClient
                .sql(UPSERT)
                .param(team.arenaId())
                .param(References.required(registry, team.leaderId()))
                .param(team.wins())
                .update();
    }
}
