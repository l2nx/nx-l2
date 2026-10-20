/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.forum;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.forum.PostRepository;
import app.l2nx.highfive.repository.forum.model.PostRow;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostRepositoryTest extends RepositoryTest {
    private final PostRepository repository = new PostRepository(JDBC, registry);

    private static int insertTopic(UUID owner) {
        return JDBC.sql("""
            INSERT INTO topics (forum_id, character_id, title, topic_type, created_at)
            VALUES (?, ?, 'Topic', 'NORMAL', now())
            RETURNING id\
            """)
                .param(TopicRepositoryTest.insertForum())
                .param(owner)
                .query(Integer.class)
                .single();
    }

    private PostRow post(int topicId, UUID owner, String content) {
        return PostRow.builder()
                .topicId(topicId)
                .ownerId(registry.handleOf(owner))
                .ownerName(TopicRepositoryTest.nameOf(owner))
                .createdAt(NOW)
                .content(content)
                .build();
    }

    private static Boolean contentIsNull(long postId) {
        return JDBC.sql("SELECT content IS NULL FROM posts WHERE id = ?")
                .param(postId)
                .query(Boolean.class)
                .single();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreOwnerUuid_whenCreated() {
            final UUID owner = insertCharacter();

            final long id = repository.insert(post(insertTopic(owner), owner, "Text"));

            assertThat(JDBC.sql("SELECT character_id FROM posts WHERE id = ?")
                            .param(id)
                            .query(UUID.class)
                            .single())
                    .isEqualTo(owner);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " "})
        void insert_shouldStoreNullContent_whenContentIsBlank(String content) {
            final UUID owner = insertCharacter();

            final long id = repository.insert(post(insertTopic(owner), owner, content));

            assertThat(contentIsNull(id)).isTrue();
        }
    }

    @Nested
    class FindByTopic {
        @Test
        void findByTopic_shouldReturnOldestFirstWithOwnerName_whenPostsExist() {
            final UUID owner = insertCharacter();
            final int topic = insertTopic(owner);
            final PostRow first = post(topic, owner, "One");
            final PostRow second = post(topic, owner, null);
            final long firstId = repository.insert(first);
            final long secondId = repository.insert(second);

            assertThat(repository.findByTopic(topic))
                    .containsExactly(
                            first.toBuilder().id(firstId).build(),
                            second.toBuilder().id(secondId).build());
        }
    }

    @Nested
    class DeleteByTopic {
        @Test
        void deleteByTopic_shouldRemoveOnlyThatTopicsPosts_whenCalled() {
            final UUID owner = insertCharacter();
            final int topic = insertTopic(owner);
            final int otherTopic = insertTopic(owner);
            repository.insert(post(topic, owner, "One"));
            repository.insert(post(otherTopic, owner, "Two"));

            repository.deleteByTopic(topic);

            assertThat(repository.findByTopic(topic)).isEmpty();
            assertThat(repository.findByTopic(otherTopic)).hasSize(1);
        }
    }

    @Nested
    class UpdateContent {
        @Test
        void updateContent_shouldReplaceText_whenPostExists() {
            final UUID owner = insertCharacter();
            final int topic = insertTopic(owner);
            final long id = repository.insert(post(topic, owner, "Old"));

            repository.updateContent(id, "New");

            assertThat(repository.findByTopic(topic))
                    .singleElement()
                    .extracting(PostRow::content)
                    .isEqualTo("New");
        }

        @Test
        void updateContent_shouldStoreNull_whenContentIsBlank() {
            final UUID owner = insertCharacter();
            final long id = repository.insert(post(insertTopic(owner), owner, "Old"));

            repository.updateContent(id, "");

            assertThat(contentIsNull(id)).isTrue();
        }
    }
}
