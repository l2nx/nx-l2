/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterRaidPointRepository {
    private static final String SELECT_ALL_POINTS = "SELECT character_id, npc_id, points FROM character_raid_points";
    private static final String UPSERT_POINTS = """
        INSERT INTO character_raid_points (character_id, npc_id, points)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id, npc_id) DO UPDATE SET points = EXCLUDED.points\
        """;
    private static final String DELETE_ALL_POINTS = "DELETE FROM character_raid_points";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return the points of every character, by character handle and then by raid boss npc id
     */
    public Map<Integer, Map<Integer, Integer>> findAll() {
        final Map<Integer, Map<Integer, Integer>> points = new HashMap<>();
        jdbcClient.sql(SELECT_ALL_POINTS).query(rs -> {
            points.computeIfAbsent(registry.handleOf(rs.getObject("character_id", UUID.class)), key -> new HashMap<>())
                    .put(rs.getInt("npc_id"), rs.getInt("points"));
        });
        return points;
    }

    public void save(int objectId, int npcId, int points) {
        jdbcClient
                .sql(UPSERT_POINTS)
                .param(References.required(registry, objectId))
                .param(npcId)
                .param(points)
                .update();
    }

    public void deleteAll() {
        jdbcClient.sql(DELETE_ALL_POINTS).update();
    }
}
