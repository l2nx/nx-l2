/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.communitybbs.BB;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.communitybbs.BB.Post.CPost;
import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.repository.forum.ForumRepository;
import app.l2nx.highfive.repository.forum.PostRepository;
import app.l2nx.highfive.repository.forum.TopicRepository;
import app.l2nx.highfive.repository.forum.model.PostRow;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

// Post reaches its persistence through the static ForumsBBSManager bridge, which each test rebinds.
@Execution(ExecutionMode.SAME_THREAD)
class PostTest {
    private static final int TOPIC_ID = 7;
    private static final int FORUM_ID = 3;
    // Enough posts that an unordered (hash) collection practically never iterates them in insertion order.
    private static final long[] POST_IDS = LongStream.rangeClosed(1, 16).toArray();

    private final PostRepository posts = mock(PostRepository.class);
    private final Topic topic = mock(Topic.class);

    @BeforeEach
    void bindForumsBridge() {
        new ForumsBBSManager(mock(ForumRepository.class), mock(TopicRepository.class), posts);
        when(topic.getID()).thenReturn(TOPIC_ID);
        when(topic.getForumID()).thenReturn(FORUM_ID);
        when(posts.findByTopic(TOPIC_ID))
                .thenReturn(LongStream.of(POST_IDS).mapToObj(PostTest::row).toList());
    }

    @Nested
    class GetCPost {
        @Test
        void getCPost_shouldReturnNthPostOfTopic_whenLoaded() {
            final Post post = new Post(topic);

            final List<Long> actual = IntStream.range(0, POST_IDS.length)
                    .mapToObj(post::getCPost)
                    .map(CPost::getPostId)
                    .toList();

            assertThat(actual)
                    .containsExactlyElementsOf(LongStream.of(POST_IDS).boxed().toList());
        }

        @Test
        void getCPost_shouldReturnNull_whenIndexIsPastLastPost() {
            final Post post = new Post(topic);

            assertThat(post.getCPost(POST_IDS.length)).isNull();
        }
    }

    @Nested
    class UpdateText {
        @Test
        void updateText_shouldStoreTextOfNthPost() {
            final Post post = new Post(topic);
            final int index = 5;
            post.getCPost(index).setPostText("edited");

            post.updateText(index);

            verify(posts).updateContent(POST_IDS[index], "edited");
        }
    }

    private static PostRow row(long id) {
        return PostRow.builder()
                .id(id)
                .topicId(TOPIC_ID)
                .ownerId(1)
                .ownerName("Owner")
                .createdAt(Instant.ofEpochSecond(id))
                .content("post " + id)
                .build();
    }
}
