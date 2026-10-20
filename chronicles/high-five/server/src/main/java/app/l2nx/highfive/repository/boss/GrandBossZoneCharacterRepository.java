/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.boss;

import app.l2nx.engine.core.identity.IdentityRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class GrandBossZoneCharacterRepository {
    private static final String SELECT_ALL =
            "SELECT zone_id, character_id FROM grand_boss_zone_characters ORDER BY zone_id, character_id";
    private static final String DELETE_ALL = "DELETE FROM grand_boss_zone_characters";
    private static final String INSERT = "INSERT INTO grand_boss_zone_characters (character_id, zone_id) VALUES (?, ?)";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    /**
     * @return character object ids by zone id; zones without characters are absent
     */
    public Map<Integer, List<Integer>> findAll() {
        final Map<Integer, List<Integer>> zones = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            zones.computeIfAbsent(rs.getInt("zone_id"), zoneId -> new ArrayList<>())
                    .add(registry.handleOf(rs.getObject("character_id", UUID.class)));
        });
        return zones;
    }

    @Transactional
    public void replaceAll(Map<Integer, ? extends Collection<Integer>> charactersByZone) {
        jdbcClient.sql(DELETE_ALL).update();
        final List<Object[]> rows = new ArrayList<>();
        charactersByZone.forEach((zoneId, objectIds) -> {
            for (int objectId : objectIds) {
                // A character deleted since it entered the zone has no row to reference.
                registry.findId(objectId).ifPresent(id -> rows.add(new Object[] {id, zoneId}));
            }
        });
        jdbcTemplate.batchUpdate(INSERT, rows);
    }
}
