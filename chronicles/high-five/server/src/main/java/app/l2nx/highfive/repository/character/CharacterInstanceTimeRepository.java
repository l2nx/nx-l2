/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterInstanceTimeRepository {
    private static final String SELECT_TIMES =
            "SELECT instance_template_id, reenter_at FROM character_instance_times WHERE character_id = ?";
    private static final String UPSERT_TIME = """
        INSERT INTO character_instance_times (character_id, instance_template_id, reenter_at)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id, instance_template_id) DO UPDATE SET reenter_at = EXCLUDED.reenter_at\
        """;
    private static final String DELETE_TIME =
            "DELETE FROM character_instance_times WHERE character_id = ? AND instance_template_id = ?";
    private static final String DELETE_EXPIRED = "DELETE FROM character_instance_times WHERE reenter_at <= ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Map<Integer, Instant> findAll(int objectId) {
        final Map<Integer, Instant> times = new LinkedHashMap<>();
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(SELECT_TIMES).param(id).query(rs -> {
                    times.put(rs.getInt("instance_template_id"), JdbcTimes.instant(rs, "reenter_at"));
                }));
        return times;
    }

    public void save(int objectId, int instanceTemplateId, Instant reenterAt) {
        jdbcClient
                .sql(UPSERT_TIME)
                .param(References.required(registry, objectId))
                .param(instanceTemplateId)
                .param(JdbcTimes.utc(reenterAt))
                .update();
    }

    public void delete(int objectId, int instanceTemplateId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_TIME)
                        .param(id)
                        .param(instanceTemplateId)
                        .update());
    }

    public void deleteExpired(Instant now) {
        jdbcClient.sql(DELETE_EXPIRED).param(JdbcTimes.utc(now)).update();
    }
}
