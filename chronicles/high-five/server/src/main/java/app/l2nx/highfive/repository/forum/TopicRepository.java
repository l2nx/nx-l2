/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.forum.model.TopicRow;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TopicRepository {
    // Index = the Topic type constants.
    private static final List<String> TYPES = List.of("NORMAL", "MEMO");

    private static final String SELECT_BY_FORUM = """
        SELECT t.id, t.forum_id, t.title, t.created_at, t.character_id, c.name, t.topic_type
        FROM topics t
        JOIN characters c ON c.id = t.character_id
        WHERE t.forum_id = ?
        ORDER BY t.id DESC\
        """;
    private static final String INSERT = """
        INSERT INTO topics (forum_id, character_id, title, topic_type, created_at)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id\
        """;
    private static final String DELETE = "DELETE FROM topics WHERE id = ? AND forum_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return the forum's topics, newest first
     */
    public List<TopicRow> findByForum(int forumId) {
        return jdbcClient
                .sql(SELECT_BY_FORUM)
                .param(forumId)
                .query((rs, rowNum) -> TopicRow.builder()
                        .id(rs.getInt("id"))
                        .forumId(rs.getInt("forum_id"))
                        .title(rs.getString("title"))
                        .createdAt(JdbcTimes.instant(rs, "created_at"))
                        .ownerId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .ownerName(rs.getString("name"))
                        .type(TYPES.indexOf(rs.getString("topic_type")))
                        .build())
                .list();
    }

    /**
     * @return the id of the new topic; {@code topic.id()} and {@code topic.ownerName()} are ignored
     */
    public int insert(TopicRow topic) {
        return jdbcClient
                .sql(INSERT)
                .param(topic.forumId())
                .param(References.required(registry, topic.ownerId()))
                .param(topic.title())
                .param(TYPES.get(topic.type()))
                .param(JdbcTimes.utc(topic.createdAt()))
                .query(Integer.class)
                .single();
    }

    /**
     * Deletes the topic with its posts.
     */
    public void delete(int topicId, int forumId) {
        jdbcClient.sql(DELETE).param(topicId).param(forumId).update();
    }
}
