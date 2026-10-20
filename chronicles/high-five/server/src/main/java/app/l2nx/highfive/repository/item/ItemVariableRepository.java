/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named text variables of an item. The item row must exist first.
 */
@Repository
@RequiredArgsConstructor
public class ItemVariableRepository {
    private static final String SELECT_VARIABLES =
            "SELECT name, content FROM item_variables WHERE item_id = ? ORDER BY name";
    private static final String EXISTS_VARIABLES = "SELECT EXISTS (SELECT 1 FROM item_variables WHERE item_id = ?)";
    private static final String UPSERT_VARIABLE = """
        INSERT INTO item_variables (item_id, name, content)
        VALUES (?, ?, ?)
        ON CONFLICT (item_id, name) DO UPDATE SET content = EXCLUDED.content\
        """;
    private static final String DELETE_VARIABLE = "DELETE FROM item_variables WHERE item_id = ? AND name = ?";
    private static final String DELETE_VARIABLES = "DELETE FROM item_variables WHERE item_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public Map<String, String> findAll(int objectId) {
        final Map<String, String> variables = new LinkedHashMap<>();
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(SELECT_VARIABLES).param(id).query((RowCallbackHandler)
                        rs -> variables.put(rs.getString("name"), rs.getString("content"))));
        return variables;
    }

    public boolean exists(int objectId) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(EXISTS_VARIABLES)
                        .param(id)
                        .query(Boolean.class)
                        .single())
                .orElse(false);
    }

    /**
     * Applies the changes of one save. A variable set to an empty value is deleted: an empty value is no value.
     */
    @Transactional
    public void save(int objectId, Collection<String> deleted, Map<String, String> changed) {
        final UUID itemId = References.required(registry, objectId);
        final List<Object[]> deletes = new ArrayList<>();
        final List<Object[]> upserts = new ArrayList<>();
        deleted.forEach(name -> deletes.add(new Object[] {itemId, name}));
        changed.forEach((name, content) -> {
            if ((content == null) || content.isEmpty()) {
                deletes.add(new Object[] {itemId, name});
            } else {
                upserts.add(new Object[] {itemId, name, content});
            }
        });
        jdbcTemplate.batchUpdate(DELETE_VARIABLE, deletes);
        jdbcTemplate.batchUpdate(UPSERT_VARIABLE, upserts);
    }

    public void deleteAll(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_VARIABLES).param(id).update());
    }
}
