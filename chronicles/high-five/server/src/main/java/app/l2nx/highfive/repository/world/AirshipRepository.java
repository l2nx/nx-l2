/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AirshipRepository {
    private static final String SELECT_ALL = "SELECT clan_id, fuel FROM airships";
    private static final String UPSERT = """
        INSERT INTO airships (clan_id, fuel)
        VALUES (?, ?)
        ON CONFLICT (clan_id) DO UPDATE SET fuel = EXCLUDED.fuel\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return fuel by owning clan id
     */
    public Map<Integer, Integer> findAll() {
        final Map<Integer, Integer> fuelByClan = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            fuelByClan.put(registry.handleOf(rs.getObject("clan_id", UUID.class)), rs.getInt("fuel"));
        });
        return fuelByClan;
    }

    public void save(int clanId, int fuel) {
        jdbcClient
                .sql(UPSERT)
                .param(References.required(registry, clanId))
                .param(fuel)
                .update();
    }
}
