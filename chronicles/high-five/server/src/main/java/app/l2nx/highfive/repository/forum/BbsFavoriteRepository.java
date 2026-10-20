/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.forum.model.BbsFavorite;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BbsFavoriteRepository {
    private static final String SELECT_BY_CHARACTER = """
        SELECT id, title, bypass, added_at
        FROM bbs_favorites
        WHERE character_id = ?
        ORDER BY added_at DESC, id DESC\
        """;
    private static final String COUNT_BY_CHARACTER = "SELECT count(*) FROM bbs_favorites WHERE character_id = ?";
    private static final String UPSERT = """
        INSERT INTO bbs_favorites (character_id, title, bypass, added_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (character_id, bypass) DO UPDATE SET title = EXCLUDED.title, added_at = EXCLUDED.added_at\
        """;
    private static final String DELETE = "DELETE FROM bbs_favorites WHERE character_id = ? AND id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return the favorites, newest first
     */
    public List<BbsFavorite> findByCharacter(int characterId) {
        return registry.findId(characterId)
                .map(refId -> jdbcClient
                        .sql(SELECT_BY_CHARACTER)
                        .param(refId)
                        .query((rs, rowNum) -> BbsFavorite.builder()
                                .id(rs.getInt("id"))
                                .title(rs.getString("title"))
                                .bypass(rs.getString("bypass"))
                                .addedAt(JdbcTimes.instant(rs, "added_at"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    public int countByCharacter(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(COUNT_BY_CHARACTER)
                        .param(id)
                        .query(Integer.class)
                        .single())
                .orElse(0);
    }

    /**
     * Adds the link, or renames and moves to the top a link already bookmarked.
     */
    public void save(int characterId, String title, String bypass, Instant now) {
        jdbcClient
                .sql(UPSERT)
                .param(References.required(registry, characterId))
                .param(title)
                .param(bypass)
                .param(JdbcTimes.utc(now))
                .update();
    }

    public boolean delete(int characterId, int favoriteId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient.sql(DELETE).param(id).param(favoriteId).update() > 0)
                .orElse(false);
    }
}
