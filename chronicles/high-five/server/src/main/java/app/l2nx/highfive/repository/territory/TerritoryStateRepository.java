/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.territory;

import java.sql.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Wards owned by each territory. A territory without a row owns only its own ward.
 */
@Repository
@RequiredArgsConstructor
public class TerritoryStateRepository {
    private static final String SELECT_ALL =
            "SELECT territory_id, owned_ward_territory_ids FROM territory_states ORDER BY territory_id";
    private static final String UPSERT = """
        INSERT INTO territory_states (territory_id, owned_ward_territory_ids) VALUES (?, ?)
        ON CONFLICT (territory_id) DO UPDATE SET owned_ward_territory_ids = EXCLUDED.owned_ward_territory_ids\
        """;

    private final JdbcClient jdbcClient;

    /**
     * @return owned ward territory ids by territory id, only for territories with a stored state
     */
    public Map<Integer, List<Integer>> findAll() {
        final Map<Integer, List<Integer>> wards = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            final Array array = rs.getArray("owned_ward_territory_ids");
            wards.put(rs.getInt("territory_id"), List.of((Integer[]) array.getArray()));
        });
        return wards;
    }

    public void save(int territoryId, Collection<Integer> ownedWardTerritoryIds) {
        jdbcClient
                .sql(UPSERT)
                .param(territoryId)
                .param(ownedWardTerritoryIds.toArray(Integer[]::new))
                .update();
    }
}
