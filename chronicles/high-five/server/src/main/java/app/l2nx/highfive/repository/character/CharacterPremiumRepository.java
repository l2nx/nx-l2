/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterPremiumRepository {
    private static final String SELECT_ENDS_AT = "SELECT ends_at FROM character_premiums WHERE character_id = ?";
    private static final String UPSERT = """
        INSERT INTO character_premiums (character_id, ends_at)
        VALUES (?, ?)
        ON CONFLICT (character_id) DO UPDATE SET ends_at = EXCLUDED.ends_at\
        """;
    private static final String DELETE = "DELETE FROM character_premiums WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Optional<Instant> findEndsAt(UUID characterId) {
        return jdbcClient
                .sql(SELECT_ENDS_AT)
                .param(characterId)
                .query((rs, rowNum) -> JdbcTimes.instant(rs, "ends_at"))
                .optional();
    }

    public Optional<Instant> findEndsAt(int characterId) {
        return registry.findId(characterId).flatMap(this::findEndsAt);
    }

    public void save(int characterId, Instant endsAt) {
        jdbcClient
                .sql(UPSERT)
                .param(References.required(registry, characterId))
                .param(JdbcTimes.utc(endsAt))
                .update();
    }

    public void delete(int characterId) {
        registry.findId(characterId)
                .ifPresent(id -> jdbcClient.sql(DELETE).param(id).update());
    }
}
