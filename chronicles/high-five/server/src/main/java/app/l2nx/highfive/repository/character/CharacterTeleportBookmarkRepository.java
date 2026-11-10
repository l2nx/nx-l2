/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterTeleportBookmark;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * An empty tag is stored as {@code NULL} and read back as {@code null}.
 */
@Repository
@RequiredArgsConstructor
public class CharacterTeleportBookmarkRepository {
    private static final String SELECT_BOOKMARKS = """
        SELECT bookmark_id, x, y, z, icon, tag, name
        FROM character_teleport_bookmarks
        WHERE character_id = ?
        ORDER BY bookmark_id\
        """;
    private static final String INSERT_BOOKMARK = """
        INSERT INTO character_teleport_bookmarks (character_id, bookmark_id, x, y, z, icon, tag, name)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String UPDATE_BOOKMARK = """
        UPDATE character_teleport_bookmarks SET icon = ?, tag = ?, name = ?
        WHERE character_id = ? AND bookmark_id = ?\
        """;
    private static final String DELETE_BOOKMARK =
            "DELETE FROM character_teleport_bookmarks WHERE character_id = ? AND bookmark_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterTeleportBookmark> findAll(UUID characterId) {
        return jdbcClient
                .sql(SELECT_BOOKMARKS)
                .param(characterId)
                .query((rs, rowNum) -> CharacterTeleportBookmark.builder()
                        .bookmarkId(rs.getInt("bookmark_id"))
                        .x(rs.getInt("x"))
                        .y(rs.getInt("y"))
                        .z(rs.getInt("z"))
                        .icon(rs.getInt("icon"))
                        .tag(rs.getString("tag"))
                        .name(rs.getString("name"))
                        .build())
                .list();
    }

    public List<CharacterTeleportBookmark> findAll(int objectId) {
        return registry.findId(objectId).map(this::findAll).orElse(List.of());
    }

    public void insert(int objectId, CharacterTeleportBookmark bookmark) {
        jdbcClient
                .sql(INSERT_BOOKMARK)
                .param(References.required(registry, objectId))
                .param(bookmark.bookmarkId())
                .param(bookmark.x())
                .param(bookmark.y())
                .param(bookmark.z())
                .param(bookmark.icon())
                .param(textOrNull(bookmark.tag()))
                .param(bookmark.name())
                .update();
    }

    public void update(int objectId, int bookmarkId, int icon, String tag, String name) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_BOOKMARK)
                        .param(icon)
                        .param(textOrNull(tag))
                        .param(name)
                        .param(id)
                        .param(bookmarkId)
                        .update());
    }

    public void delete(int objectId, int bookmarkId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_BOOKMARK)
                        .param(id)
                        .param(bookmarkId)
                        .update());
    }
}
