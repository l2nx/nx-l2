/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.pet;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.pet.model.PetRow;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Pets, keyed by their control item; a pet goes with its control item.
 */
@Repository
@RequiredArgsConstructor
public class PetRepository {
    private static final String SELECT_RESTORABLE = "SELECT character_id, item_id FROM pets WHERE restore_on_login";
    private static final String SELECT_PET = """
        SELECT item_id, character_id, name, pet_level, current_hp, current_mp, exp, sp, fed, restore_on_login
        FROM pets
        WHERE item_id = ?\
        """;
    private static final String EXISTS_NAME = """
        SELECT EXISTS (
            SELECT 1
            FROM pets p
            JOIN items i ON i.id = p.item_id
            WHERE p.name = ? AND i.item_template_id = ?)\
        """;
    private static final String UPSERT_PET = """
        INSERT INTO pets (
            item_id, character_id, name, pet_level, current_hp, current_mp, exp, sp, fed, restore_on_login)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (item_id) DO UPDATE
        SET character_id = EXCLUDED.character_id, name = EXCLUDED.name, pet_level = EXCLUDED.pet_level,
            current_hp = EXCLUDED.current_hp, current_mp = EXCLUDED.current_mp, exp = EXCLUDED.exp, sp = EXCLUDED.sp,
            fed = EXCLUDED.fed, restore_on_login = EXCLUDED.restore_on_login\
        """;
    private static final String UPDATE_FED = "UPDATE pets SET fed = ? WHERE item_id = ?";
    private static final String DELETE_PET = "DELETE FROM pets WHERE item_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return the control item of the pet to summon on login, by owner
     */
    public Map<Integer, Integer> findRestorable() {
        final Map<Integer, Integer> pets = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_RESTORABLE).query((RowCallbackHandler) rs -> pets.put(
                registry.handleOf(rs.getObject("character_id", UUID.class)),
                registry.handleOf(rs.getObject("item_id", UUID.class))));
        return pets;
    }

    public Optional<PetRow> find(int controlItemId) {
        return registry.findId(controlItemId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_PET)
                        .param(id)
                        .query((rs, rowNum) -> PetRow.builder()
                                .controlItemId(registry.handleOf(rs.getObject("item_id", UUID.class)))
                                .ownerId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                                .name(rs.getString("name"))
                                .level(rs.getInt("pet_level"))
                                .currentHp(rs.getInt("current_hp"))
                                .currentMp(rs.getInt("current_mp"))
                                .exp(rs.getLong("exp"))
                                .sp(rs.getLong("sp"))
                                .fed(rs.getInt("fed"))
                                .restore(rs.getBoolean("restore_on_login"))
                                .build())
                        .optional());
    }

    /**
     * @return whether a pet of the same control item template already has the name, ignoring case
     */
    public boolean nameExists(String name, int controlItemTemplateId) {
        return jdbcClient
                .sql(EXISTS_NAME)
                .param(name)
                .param(controlItemTemplateId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Inserts a new pet or updates a stored one. The control item must already be stored.
     */
    public void save(PetRow pet) {
        jdbcClient
                .sql(UPSERT_PET)
                .param(References.required(registry, pet.controlItemId()))
                .param(References.required(registry, pet.ownerId()))
                .param(textOrNull(pet.name()))
                .param(pet.level())
                .param(pet.currentHp())
                .param(pet.currentMp())
                .param(pet.exp())
                .param(pet.sp())
                .param(pet.fed())
                .param(pet.restore())
                .update();
    }

    public void updateFed(int controlItemId, int fed) {
        registry.findId(controlItemId)
                .ifPresent(id -> jdbcClient.sql(UPDATE_FED).param(fed).param(id).update());
    }

    public void delete(int controlItemId) {
        registry.findId(controlItemId)
                .ifPresent(id -> jdbcClient.sql(DELETE_PET).param(id).update());
    }
}
