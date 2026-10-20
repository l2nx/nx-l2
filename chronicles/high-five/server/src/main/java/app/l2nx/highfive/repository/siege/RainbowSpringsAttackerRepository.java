/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Clans registered for the Rainbow Springs Chateau siege with the War Decrees they handed in.
 */
@Repository
@RequiredArgsConstructor
public class RainbowSpringsAttackerRepository {
    private static final String SELECT_ALL =
            "SELECT clan_id, war_decree_count FROM rainbow_springs_attackers ORDER BY clan_id";
    private static final String INSERT =
            "INSERT INTO rainbow_springs_attackers (clan_id, war_decree_count) VALUES (?, ?)";
    private static final String DELETE = "DELETE FROM rainbow_springs_attackers WHERE clan_id = ?";
    private static final String DELETE_ALL = "DELETE FROM rainbow_springs_attackers";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return War Decree count by clan handle
     */
    public Map<Integer, Long> findAll() {
        final Map<Integer, Long> counts = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            counts.put(registry.handleOf(rs.getObject("clan_id", UUID.class)), rs.getLong("war_decree_count"));
        });
        return counts;
    }

    public void insert(int clanId, long warDecreeCount) {
        jdbcClient
                .sql(INSERT)
                .param(References.required(registry, clanId))
                .param(warDecreeCount)
                .update();
    }

    public void delete(int clanId) {
        registry.findId(clanId).ifPresent(id -> jdbcClient.sql(DELETE).param(id).update());
    }

    public void deleteAll() {
        jdbcClient.sql(DELETE_ALL).update();
    }
}
