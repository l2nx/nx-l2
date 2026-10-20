/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterShortcut;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * An {@code ITEM} shortcut stores the item instance, so it goes with the item when the item is destroyed; every other
 * type stores its numeric target.
 */
@Repository
@RequiredArgsConstructor
public class CharacterShortcutRepository {
    private static final String SELECT_SHORTCUTS = """
        SELECT slot, page, shortcut_type, target_id, item_id, skill_level
        FROM character_shortcuts
        WHERE character_id = ? AND class_index = ?\
        """;
    private static final String UPSERT_SHORTCUT = """
        INSERT INTO character_shortcuts (
            character_id, class_index, page, slot, shortcut_type, target_id, item_id, skill_level)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (character_id, class_index, page, slot) DO UPDATE
        SET shortcut_type = EXCLUDED.shortcut_type, target_id = EXCLUDED.target_id, item_id = EXCLUDED.item_id,
            skill_level = EXCLUDED.skill_level\
        """;
    private static final String DELETE_SHORTCUT =
            "DELETE FROM character_shortcuts WHERE character_id = ? AND class_index = ? AND page = ? AND slot = ?";
    private static final String DELETE_CLASS_SHORTCUTS =
            "DELETE FROM character_shortcuts WHERE character_id = ? AND class_index = ?";
    private static final String DELETE_ALL_SHORTCUTS = "DELETE FROM character_shortcuts WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterShortcut> findAll(int objectId, int classIndex) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_SHORTCUTS)
                        .param(id)
                        .param(classIndex)
                        .query(this::mapShortcut)
                        .list())
                .orElse(List.of());
    }

    /**
     * Stores the shortcut in its slot, replacing the one there. An {@code ITEM} shortcut needs its item stored.
     */
    public void save(int objectId, int classIndex, CharacterShortcut shortcut) {
        final boolean item = shortcut.type() == ShortcutType.ITEM;
        final boolean skill = (shortcut.type() == ShortcutType.SKILL) && (shortcut.level() > 0);
        jdbcClient
                .sql(UPSERT_SHORTCUT)
                .param(References.required(registry, objectId))
                .param(classIndex)
                .param(shortcut.page())
                .param(shortcut.slot())
                .param(shortcut.type().name())
                .param(item ? null : shortcut.id())
                .param(item ? References.required(registry, shortcut.id()) : null)
                .param(skill ? shortcut.level() : null)
                .update();
    }

    public void delete(int objectId, int classIndex, int slot, int page) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_SHORTCUT)
                        .param(id)
                        .param(classIndex)
                        .param(page)
                        .param(slot)
                        .update());
    }

    public void deleteAll(int objectId, int classIndex) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_CLASS_SHORTCUTS)
                        .param(id)
                        .param(classIndex)
                        .update());
    }

    /**
     * Deletes the shortcuts of every class of the character.
     */
    public void deleteAll(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_ALL_SHORTCUTS).param(id).update());
    }

    private CharacterShortcut mapShortcut(ResultSet rs, int rowNum) throws SQLException {
        final ShortcutType type = ShortcutType.valueOf(rs.getString("shortcut_type"));
        return CharacterShortcut.builder()
                .slot(rs.getInt("slot"))
                .page(rs.getInt("page"))
                .type(type)
                .id(
                        type == ShortcutType.ITEM
                                ? registry.handleOf(rs.getObject("item_id", UUID.class))
                                : rs.getInt("target_id"))
                .level(rs.getInt("skill_level"))
                .build();
    }
}
