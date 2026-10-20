/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.forum.model.PostRow;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostRepository {
    private static final String SELECT_BY_TOPIC = """
        SELECT p.id, p.topic_id, p.character_id, c.name, p.created_at, p.content
        FROM posts p
        JOIN characters c ON c.id = p.character_id
        WHERE p.topic_id = ?
        ORDER BY p.id\
        """;
    private static final String INSERT = """
        INSERT INTO posts (topic_id, character_id, content, created_at)
        VALUES (?, ?, ?, ?)
        RETURNING id\
        """;
    private static final String DELETE_BY_TOPIC = "DELETE FROM posts WHERE topic_id = ?";
    private static final String UPDATE_CONTENT = "UPDATE posts SET content = ? WHERE id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<PostRow> findByTopic(int topicId) {
        return jdbcClient
                .sql(SELECT_BY_TOPIC)
                .param(topicId)
                .query((rs, rowNum) -> PostRow.builder()
                        .id(rs.getLong("id"))
                        .topicId(rs.getInt("topic_id"))
                        .ownerId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .ownerName(rs.getString("name"))
                        .createdAt(JdbcTimes.instant(rs, "created_at"))
                        .content(rs.getString("content"))
                        .build())
                .list();
    }

    /**
     * @return the id of the new post; {@code post.id()} and {@code post.ownerName()} are ignored, a blank content is
     *     stored as none
     */
    public long insert(PostRow post) {
        return jdbcClient
                .sql(INSERT)
                .param(post.topicId())
                .param(References.required(registry, post.ownerId()))
                .param(blankToNull(post.content()))
                .param(JdbcTimes.utc(post.createdAt()))
                .query(Long.class)
                .single();
    }

    public void deleteByTopic(int topicId) {
        jdbcClient.sql(DELETE_BY_TOPIC).param(topicId).update();
    }

    public void updateContent(long postId, String content) {
        jdbcClient.sql(UPDATE_CONTENT).param(blankToNull(content)).param(postId).update();
    }

    private static String blankToNull(String content) {
        return StringUtils.isBlank(content) ? null : content;
    }
}
