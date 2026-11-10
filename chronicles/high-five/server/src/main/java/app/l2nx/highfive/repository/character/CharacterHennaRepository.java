/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterHenna;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterHennaRepository {
    private static final String SELECT_HENNAS =
            "SELECT slot, henna_id FROM character_hennas WHERE character_id = ? AND class_index = ? ORDER BY slot";
    private static final String INSERT_HENNA =
            "INSERT INTO character_hennas (character_id, class_index, slot, henna_id) VALUES (?, ?, ?, ?)";
    private static final String DELETE_HENNA =
            "DELETE FROM character_hennas WHERE character_id = ? AND class_index = ? AND slot = ?";
    private static final String DELETE_CLASS_HENNAS =
            "DELETE FROM character_hennas WHERE character_id = ? AND class_index = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterHenna> findAll(UUID characterId, int classIndex) {
        return jdbcClient
                .sql(SELECT_HENNAS)
                .param(characterId)
                .param(classIndex)
                .query((rs, rowNum) -> CharacterHenna.builder()
                        .slot(rs.getInt("slot"))
                        .hennaId(rs.getInt("henna_id"))
                        .build())
                .list();
    }

    public List<CharacterHenna> findAll(int objectId, int classIndex) {
        return registry.findId(objectId).map(id -> findAll(id, classIndex)).orElse(List.of());
    }

    /**
     * @param henna the slot, 1 to 3, and the henna in it
     */
    public void insert(int objectId, int classIndex, CharacterHenna henna) {
        jdbcClient
                .sql(INSERT_HENNA)
                .param(References.required(registry, objectId))
                .param(classIndex)
                .param(henna.slot())
                .param(henna.hennaId())
                .update();
    }

    public void delete(int objectId, int classIndex, int slot) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_HENNA)
                        .param(id)
                        .param(classIndex)
                        .param(slot)
                        .update());
    }

    public void deleteAll(int objectId, int classIndex) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_CLASS_HENNAS)
                        .param(id)
                        .param(classIndex)
                        .update());
    }
}
