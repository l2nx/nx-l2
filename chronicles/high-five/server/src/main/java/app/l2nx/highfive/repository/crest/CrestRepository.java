/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.crest;

import app.l2nx.highfive.entity.clan.Crest;
import app.l2nx.highfive.entity.clan.enums.CrestType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Clan and alliance crest images. The client caches an image by crest id, so the database assigns ids that are never
 * reused, and an unused crest can be deleted at any time.
 */
@Repository
@RequiredArgsConstructor
public class CrestRepository {
    private static final String SELECT_CRESTS = "SELECT id, crest_type, image FROM crests ORDER BY id";
    private static final String INSERT_CREST = "INSERT INTO crests (crest_type, image) VALUES (?, ?) RETURNING id";
    private static final String DELETE_CREST = "DELETE FROM crests WHERE id = ?";

    private final JdbcClient jdbcClient;

    public List<Crest> findAll() {
        return jdbcClient
                .sql(SELECT_CRESTS)
                .query((rs, rowNum) ->
                        new Crest(rs.getInt("id"), rs.getBytes("image"), CrestType.valueOf(rs.getString("crest_type"))))
                .list();
    }

    public Crest create(byte[] data, CrestType type) {
        final int id = jdbcClient
                .sql(INSERT_CREST)
                .param(type.name())
                .param(data)
                .query(Integer.class)
                .single();
        return new Crest(id, data, type);
    }

    /**
     * The clans showing the crest lose it.
     */
    public void delete(int crestId) {
        jdbcClient.sql(DELETE_CREST).param(crestId).update();
    }
}
