/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.social;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterContactRepository {
    private static final String SELECT_CONTACTS =
            "SELECT contact_character_id FROM character_contacts WHERE character_id = ?";
    private static final String SELECT_CONTACT_NAMES = """
        SELECT c.name
        FROM character_contacts cc
        JOIN characters c ON c.id = cc.contact_character_id
        WHERE cc.character_id = ?\
        """;
    private static final String INSERT = """
        INSERT INTO character_contacts (character_id, contact_character_id)
        VALUES (?, ?)
        ON CONFLICT (character_id, contact_character_id) DO NOTHING\
        """;
    private static final String DELETE =
            "DELETE FROM character_contacts WHERE character_id = ? AND contact_character_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<Integer> findContactIds(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_CONTACTS)
                        .param(id)
                        .query((rs, rowNum) -> registry.handleOf(rs.getObject("contact_character_id", UUID.class)))
                        .list())
                .orElse(List.of());
    }

    public List<String> findContactNames(UUID characterId) {
        return jdbcClient
                .sql(SELECT_CONTACT_NAMES)
                .param(characterId)
                .query(String.class)
                .list();
    }

    public void add(int characterId, int contactId) {
        jdbcClient
                .sql(INSERT)
                .param(References.required(registry, characterId))
                .param(References.required(registry, contactId))
                .update();
    }

    public void remove(int characterId, int contactId) {
        registry.findId(characterId)
                .ifPresent(id -> registry.findId(contactId)
                        .ifPresent(contact ->
                                jdbcClient.sql(DELETE).param(id).param(contact).update()));
    }
}
