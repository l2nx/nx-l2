/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterMacro;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Empty texts are stored as {@code NULL} and read back as {@code null}.
 */
@Repository
@RequiredArgsConstructor
public class CharacterMacroRepository {
    private static final String SELECT_MACROS = """
        SELECT macro_id, icon, name, description, acronym, commands
        FROM character_macros
        WHERE character_id = ?\
        """;
    private static final String UPSERT_MACRO = """
        INSERT INTO character_macros (character_id, macro_id, icon, name, description, acronym, commands)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (character_id, macro_id) DO UPDATE
        SET icon = EXCLUDED.icon, name = EXCLUDED.name, description = EXCLUDED.description,
            acronym = EXCLUDED.acronym, commands = EXCLUDED.commands\
        """;
    private static final String DELETE_MACRO = "DELETE FROM character_macros WHERE character_id = ? AND macro_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterMacro> findAll(UUID characterId) {
        return jdbcClient
                .sql(SELECT_MACROS)
                .param(characterId)
                .query((rs, rowNum) -> CharacterMacro.builder()
                        .macroId(rs.getInt("macro_id"))
                        .icon(rs.getInt("icon"))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .acronym(rs.getString("acronym"))
                        .commands(rs.getString("commands"))
                        .build())
                .list();
    }

    public List<CharacterMacro> findAll(int objectId) {
        return registry.findId(objectId).map(this::findAll).orElse(List.of());
    }

    public void save(int objectId, CharacterMacro macro) {
        jdbcClient
                .sql(UPSERT_MACRO)
                .param(References.required(registry, objectId))
                .param(macro.macroId())
                .param(macro.icon())
                .param(textOrNull(macro.name()))
                .param(textOrNull(macro.description()))
                .param(textOrNull(macro.acronym()))
                .param(textOrNull(macro.commands()))
                .update();
    }

    public void delete(int objectId, int macroId) {
        registry.findId(objectId)
                .ifPresent(id ->
                        jdbcClient.sql(DELETE_MACRO).param(id).param(macroId).update());
    }
}
