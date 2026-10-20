/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanHallFunctionRepository {
    private static final String SELECT_BY_CLAN_HALL = """
        SELECT function_type, function_level, lease, rate_ms, ends_at
        FROM clan_hall_functions
        WHERE clan_hall_id = ?
        ORDER BY function_type\
        """;
    private static final String UPSERT = """
        INSERT INTO clan_hall_functions (clan_hall_id, function_type, function_level, lease, rate_ms, ends_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (clan_hall_id, function_type) DO UPDATE
        SET function_level = EXCLUDED.function_level, lease = EXCLUDED.lease, rate_ms = EXCLUDED.rate_ms,
            ends_at = EXCLUDED.ends_at\
        """;
    private static final String DELETE = "DELETE FROM clan_hall_functions WHERE clan_hall_id = ? AND function_type = ?";
    // A hall left without an owner outside the game (its clan deleted) keeps no functions for the next owner.
    private static final String DELETE_OF_UNOWNED_HALLS = """
        DELETE FROM clan_hall_functions f
        WHERE NOT EXISTS (
                SELECT 1 FROM clan_hall_states s
                WHERE s.clan_hall_id = f.clan_hall_id AND s.owner_clan_id IS NOT NULL)
            AND NOT EXISTS (
                SELECT 1 FROM siegable_clan_hall_states s
                WHERE s.clan_hall_id = f.clan_hall_id AND s.owner_clan_id IS NOT NULL)\
        """;

    private final JdbcClient jdbcClient;

    public List<ResidenceFunction> findByClanHall(int clanHallId) {
        return jdbcClient
                .sql(SELECT_BY_CLAN_HALL)
                .param(clanHallId)
                .query(ResidenceFunctionRows.MAPPER)
                .list();
    }

    public void save(int clanHallId, ResidenceFunction function) {
        ResidenceFunctionRows.upsert(jdbcClient, UPSERT, clanHallId, function);
    }

    public void delete(int clanHallId, int functionType) {
        jdbcClient.sql(DELETE).param(clanHallId).param(functionType).update();
    }

    public int deleteOfUnownedHalls() {
        return jdbcClient.sql(DELETE_OF_UNOWNED_HALLS).update();
    }
}
