/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Unlocked transmogrification appearances, owned by a character or, when shared, by its account.
 */
@Repository
@RequiredArgsConstructor
public class CharacterTransmogRepository {
    private static final String SELECT_BY_CHARACTER = """
        SELECT item_template_id FROM character_transmogs
        WHERE character_id = ?
        ORDER BY item_template_id\
        """;
    private static final String SELECT_BY_ACCOUNT = """
        SELECT item_template_id FROM character_transmogs
        WHERE account_id = ?
        ORDER BY item_template_id\
        """;
    private static final String INSERT = """
        INSERT INTO character_transmogs (character_id, account_id, item_template_id)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id, account_id, item_template_id) DO NOTHING\
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    /**
     * @return the item template ids, ascending
     */
    public List<Integer> findByCharacter(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_BY_CHARACTER)
                        .param(id)
                        .query(Integer.class)
                        .list())
                .orElse(List.of());
    }

    /**
     * @return the item template ids, ascending
     */
    public List<Integer> findByAccount(UUID accountId) {
        return jdbcClient
                .sql(SELECT_BY_ACCOUNT)
                .param(accountId)
                .query(Integer.class)
                .list();
    }

    public void addForCharacter(int characterId, Collection<Integer> itemTemplateIds) {
        insert(References.required(registry, characterId), null, itemTemplateIds);
    }

    public void addForAccount(UUID accountId, Collection<Integer> itemTemplateIds) {
        insert(null, accountId, itemTemplateIds);
    }

    private void insert(UUID characterId, UUID accountId, Collection<Integer> itemTemplateIds) {
        if (itemTemplateIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT,
                itemTemplateIds.stream()
                        .map(itemTemplateId -> new Object[] {characterId, accountId, itemTemplateId})
                        .toList());
    }
}
