/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.forum;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.forum.TopicRepository;
import app.l2nx.highfive.repository.forum.model.TopicRow;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TopicRepositoryTest extends RepositoryTest {
    private final TopicRepository repository = new TopicRepository(JDBC, registry);

    static int insertForum() {
        return JDBC.sql("""
            INSERT INTO forums (parent_id, name, forum_type, permission)
            SELECT id, 'Forum ' || gen_random_uuid(), 'NORMAL', 'ALL' FROM forums WHERE name = 'NormalRoot'
            RETURNING id\
            """).query(Integer.class).single();
    }

    static String nameOf(UUID characterId) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    private TopicRow topic(int forumId, UUID owner, String title, int type) {
        return TopicRow.builder()
                .forumId(forumId)
                .title(title)
                .createdAt(NOW)
                .ownerId(registry.handleOf(owner))
                .ownerName(nameOf(owner))
                .type(type)
                .build();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreOwnerUuidAndTypeName_whenCreated() {
            final UUID owner = insertCharacter();

            final int id = repository.insert(topic(insertForum(), owner, "Hello", 1));

            assertThat(JDBC.sql("SELECT character_id FROM topics WHERE id = ? AND topic_type = 'MEMO'")
                            .param(id)
                            .query(UUID.class)
                            .single())
                    .isEqualTo(owner);
        }
    }

    @Nested
    class FindByForum {
        @Test
        void findByForum_shouldReturnNewestFirstWithOwnerName_whenTopicsExist() {
            final int forum = insertForum();
            final UUID owner = insertCharacter();
            final TopicRow first = topic(forum, owner, "First", 0);
            final TopicRow second = topic(forum, owner, "Second", 0);
            final int firstId = repository.insert(first);
            final int secondId = repository.insert(second);
            repository.insert(topic(insertForum(), owner, "Elsewhere", 0));

            assertThat(repository.findByForum(forum))
                    .containsExactly(
                            second.toBuilder().id(secondId).build(),
                            first.toBuilder().id(firstId).build());
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveTopicAndPosts_whenForumMatches() {
            final int forum = insertForum();
            final UUID owner = insertCharacter();
            final int id = repository.insert(topic(forum, owner, "Gone", 0));
            JDBC.sql("INSERT INTO posts (topic_id, character_id, created_at) VALUES (?, ?, now())")
                    .param(id)
                    .param(owner)
                    .update();

            repository.delete(id, forum);

            assertThat(repository.findByForum(forum)).isEmpty();
            assertThat(JDBC.sql("SELECT count(*) FROM posts WHERE topic_id = ?")
                            .param(id)
                            .query(Integer.class)
                            .single())
                    .isZero();
        }

        @Test
        void delete_shouldKeepTopic_whenForumDiffers() {
            final int forum = insertForum();
            final int id = repository.insert(topic(forum, insertCharacter(), "Kept", 0));

            repository.delete(id, insertForum());

            assertThat(repository.findByForum(forum)).hasSize(1);
        }
    }
}
