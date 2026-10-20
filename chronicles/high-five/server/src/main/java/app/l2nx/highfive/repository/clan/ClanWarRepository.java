/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan;

import static app.l2nx.highfive.repository.LegacyValues.handleOrZero;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.clan.model.ClanWar;
import app.l2nx.highfive.repository.clan.model.WarOpponent;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanWarRepository {
    private static final String SELECT_WARS = "SELECT clan_id, enemy_clan_id FROM clan_wars";
    private static final String INSERT_WAR = """
        INSERT INTO clan_wars (clan_id, enemy_clan_id)
        VALUES (?, ?)
        ON CONFLICT (clan_id, enemy_clan_id) DO NOTHING\
        """;
    private static final String DELETE_WAR = "DELETE FROM clan_wars WHERE clan_id = ? AND enemy_clan_id = ?";
    private static final String SELECT_DECLARED_ONLY = """
        SELECT c.id, c.name, c.ally_clan_id, c.ally_name
        FROM clan_wars w
        JOIN clans c ON c.id = w.enemy_clan_id
        WHERE w.clan_id = ?
          AND NOT EXISTS (
              SELECT 1 FROM clan_wars back WHERE back.clan_id = w.enemy_clan_id AND back.enemy_clan_id = w.clan_id)
        ORDER BY c.name\
        """;
    private static final String SELECT_ATTACKERS_ONLY = """
        SELECT c.id, c.name, c.ally_clan_id, c.ally_name
        FROM clan_wars w
        JOIN clans c ON c.id = w.clan_id
        WHERE w.enemy_clan_id = ?
          AND NOT EXISTS (
              SELECT 1 FROM clan_wars back WHERE back.clan_id = w.enemy_clan_id AND back.enemy_clan_id = w.clan_id)
        ORDER BY c.name\
        """;
    private static final String SELECT_MUTUAL = """
        SELECT c.id, c.name, c.ally_clan_id, c.ally_name
        FROM clan_wars w
        JOIN clans c ON c.id = w.enemy_clan_id
        WHERE w.clan_id = ?
          AND EXISTS (
              SELECT 1 FROM clan_wars back WHERE back.clan_id = w.enemy_clan_id AND back.enemy_clan_id = w.clan_id)
        ORDER BY c.name\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<ClanWar> findAll() {
        return jdbcClient
                .sql(SELECT_WARS)
                .query((rs, rowNum) -> ClanWar.builder()
                        .clanId(registry.handleOf(rs.getObject("clan_id", UUID.class)))
                        .enemyClanId(registry.handleOf(rs.getObject("enemy_clan_id", UUID.class)))
                        .build())
                .list();
    }

    public void insert(int clanId, int enemyClanId) {
        jdbcClient
                .sql(INSERT_WAR)
                .param(References.required(registry, clanId))
                .param(References.required(registry, enemyClanId))
                .update();
    }

    /**
     * Ends the war {@code clanId} declared on {@code enemyClanId}; a war declared the other way stays.
     */
    public void delete(int clanId, int enemyClanId) {
        registry.findId(clanId)
                .ifPresent(id -> registry.findId(enemyClanId)
                        .ifPresent(enemyId -> jdbcClient
                                .sql(DELETE_WAR)
                                .param(id)
                                .param(enemyId)
                                .update()));
    }

    /**
     * @return the clans the clan declared war on that did not declare war back
     */
    public List<WarOpponent> findDeclaredOnly(int clanId) {
        return findOpponents(SELECT_DECLARED_ONLY, clanId);
    }

    /**
     * @return the clans that declared war on the clan without the clan declaring war back
     */
    public List<WarOpponent> findAttackersOnly(int clanId) {
        return findOpponents(SELECT_ATTACKERS_ONLY, clanId);
    }

    /**
     * @return the clans at war with the clan in both directions
     */
    public List<WarOpponent> findMutual(int clanId) {
        return findOpponents(SELECT_MUTUAL, clanId);
    }

    private List<WarOpponent> findOpponents(String sql, int clanId) {
        return registry.findId(clanId)
                .map(refId -> jdbcClient
                        .sql(sql)
                        .param(refId)
                        .query((rs, rowNum) -> WarOpponent.builder()
                                .clanId(registry.handleOf(rs.getObject("id", UUID.class)))
                                .name(rs.getString("name"))
                                .allyId(handleOrZero(registry, rs, "ally_clan_id"))
                                .allyName(rs.getString("ally_name"))
                                .build())
                        .list())
                .orElse(List.of());
    }
}
