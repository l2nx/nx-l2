/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterItemReuse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CharacterItemReuseRepository {
    private static final String SELECT_REUSES = """
        SELECT item_id, item_template_id, reuse_delay_ms, reuse_ends_at
        FROM character_item_reuse_saves
        WHERE character_id = ?\
        """;
    private static final String INSERT_REUSE = """
        INSERT INTO character_item_reuse_saves (character_id, item_id, item_template_id, reuse_delay_ms, reuse_ends_at)
        VALUES (?, ?, ?, ?, ?)\
        """;
    private static final String DELETE_REUSES = "DELETE FROM character_item_reuse_saves WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<CharacterItemReuse> findAll(int objectId) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_REUSES)
                        .param(id)
                        .query((rs, rowNum) -> CharacterItemReuse.builder()
                                .itemObjectId(registry.handleOf(rs.getObject("item_id", UUID.class)))
                                .itemTemplateId(rs.getInt("item_template_id"))
                                .reuseDelayMs(rs.getLong("reuse_delay_ms"))
                                .reuseEndsAt(JdbcTimes.instant(rs, "reuse_ends_at"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    /**
     * Every item must already be stored.
     */
    @Transactional
    public void replaceAll(int objectId, List<CharacterItemReuse> reuses) {
        final UUID id = References.required(registry, objectId);
        deleteAll(objectId);
        jdbcTemplate.batchUpdate(
                INSERT_REUSE,
                reuses.stream()
                        .map(reuse -> new Object[] {
                            id,
                            References.required(registry, reuse.itemObjectId()),
                            reuse.itemTemplateId(),
                            reuse.reuseDelayMs(),
                            JdbcTimes.utc(reuse.reuseEndsAt())
                        })
                        .toList());
    }

    public void deleteAll(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_REUSES).param(id).update());
    }
}
