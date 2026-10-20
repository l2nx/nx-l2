/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege;

import app.l2nx.engine.core.identity.IdentityRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ClanHallSiegeAttackerRepository {
    private static final String SELECT_BY_HALL =
            "SELECT clan_id FROM clan_hall_siege_attackers WHERE clan_hall_id = ? ORDER BY clan_id";
    private static final String INSERT = "INSERT INTO clan_hall_siege_attackers (clan_hall_id, clan_id) VALUES (?, ?)";
    private static final String DELETE_BY_HALL = "DELETE FROM clan_hall_siege_attackers WHERE clan_hall_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<Integer> findClanIdsByHall(int clanHallId) {
        return jdbcClient
                .sql(SELECT_BY_HALL)
                .param(clanHallId)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("clan_id", UUID.class)))
                .list();
    }

    @Transactional
    public void replaceAll(int clanHallId, Collection<Integer> clanIds) {
        jdbcClient.sql(DELETE_BY_HALL).param(clanHallId).update();
        final List<Object[]> rows = new ArrayList<>(clanIds.size());
        for (int clanId : clanIds) {
            // A clan disbanded since it registered has no row to reference.
            registry.findId(clanId).ifPresent(id -> rows.add(new Object[] {clanHallId, id}));
        }

        jdbcTemplate.batchUpdate(INSERT, rows);
    }
}
