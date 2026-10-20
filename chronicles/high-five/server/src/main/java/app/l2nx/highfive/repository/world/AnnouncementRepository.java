/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.highfive.mechanics.announce.AnnouncementType;
import app.l2nx.highfive.repository.world.model.AnnouncementRow;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnnouncementRepository {
    private static final int REPEAT_FOREVER = -1;

    private static final String SELECT_ALL = """
        SELECT id, announcement_type, content, author, initial_delay_ms, interval_ms, repeat_count
        FROM announcements
        ORDER BY id\
        """;
    private static final String INSERT = """
        INSERT INTO announcements (announcement_type, content, author, initial_delay_ms, interval_ms, repeat_count)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING id\
        """;
    private static final String UPDATE = """
        UPDATE announcements
        SET announcement_type = ?, content = ?, author = ?, initial_delay_ms = ?, interval_ms = ?, repeat_count = ?
        WHERE id = ?\
        """;
    private static final String DELETE = "DELETE FROM announcements WHERE id = ?";

    private final JdbcClient jdbcClient;

    public List<AnnouncementRow> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> {
                    final Integer repeat = rs.getObject("repeat_count", Integer.class);
                    return AnnouncementRow.builder()
                            .id(rs.getInt("id"))
                            .type(AnnouncementType.valueOf(rs.getString("announcement_type")))
                            .content(rs.getString("content"))
                            .author(rs.getString("author"))
                            .initialDelayMs(rs.getLong("initial_delay_ms"))
                            .intervalMs(rs.getLong("interval_ms"))
                            .repeat(repeat == null ? REPEAT_FOREVER : repeat)
                            .build();
                })
                .list();
    }

    /**
     * @return the id of the new announcement; {@code announcement.id()} is ignored
     */
    public int insert(AnnouncementRow announcement) {
        return jdbcClient
                .sql(INSERT)
                .params(columns(announcement))
                .query(Integer.class)
                .single();
    }

    public void update(AnnouncementRow announcement) {
        jdbcClient
                .sql(UPDATE)
                .params(columns(announcement))
                .param(announcement.id())
                .update();
    }

    public void delete(int id) {
        jdbcClient.sql(DELETE).param(id).update();
    }

    // Only the AUTO_* types store a schedule; the schema rejects one on any other type.
    private static List<Object> columns(AnnouncementRow announcement) {
        final boolean auto = (announcement.type() == AnnouncementType.AUTO_NORMAL)
                || (announcement.type() == AnnouncementType.AUTO_CRITICAL);
        return Arrays.asList(
                announcement.type().name(),
                announcement.content(),
                announcement.author(),
                auto ? announcement.initialDelayMs() : null,
                auto ? announcement.intervalMs() : null,
                (auto && (announcement.repeat() != REPEAT_FOREVER)) ? announcement.repeat() : null);
    }
}
