/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named variables of a character; an empty value is stored as {@code NULL} and read back as {@code null}.
 */
@Repository
@RequiredArgsConstructor
public class CharacterVariableRepository {
    private static final String SELECT_VARIABLES =
            "SELECT name, content FROM character_variables WHERE character_id = ? ORDER BY name";
    private static final String UPSERT_VARIABLE = """
        INSERT INTO character_variables (character_id, name, content)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id, name) DO UPDATE SET content = EXCLUDED.content\
        """;
    private static final String DELETE_VARIABLE = "DELETE FROM character_variables WHERE character_id = ? AND name = ?";
    private static final String DELETE_ALL_VARIABLES = "DELETE FROM character_variables WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public Map<String, String> findAll(int objectId) {
        final Map<String, String> variables = new LinkedHashMap<>();
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(SELECT_VARIABLES).param(id).query(rs -> {
                    variables.put(rs.getString("name"), rs.getString("content"));
                }));
        return variables;
    }

    public void save(int objectId, String name, String content) {
        jdbcClient
                .sql(UPSERT_VARIABLE)
                .param(References.required(registry, objectId))
                .param(name)
                .param(textOrNull(content))
                .update();
    }

    @Transactional
    public void save(int objectId, Collection<String> deletedNames, Map<String, String> values) {
        final UUID id = References.required(registry, objectId);
        if (!deletedNames.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    DELETE_VARIABLE,
                    deletedNames.stream().map(name -> new Object[] {id, name}).toList());
        }

        if (!values.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    UPSERT_VARIABLE,
                    values.entrySet().stream()
                            .map(entry -> new Object[] {id, entry.getKey(), textOrNull(entry.getValue())})
                            .toList());
        }
    }

    public void deleteAll(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_ALL_VARIABLES).param(id).update());
    }
}
