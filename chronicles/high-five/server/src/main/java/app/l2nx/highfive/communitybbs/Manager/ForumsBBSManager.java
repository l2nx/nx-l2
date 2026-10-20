/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.communitybbs.Manager;

import app.l2nx.highfive.communitybbs.BB.Forum;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.repository.forum.ForumRepository;
import app.l2nx.highfive.repository.forum.PostRepository;
import app.l2nx.highfive.repository.forum.TopicRepository;
import app.l2nx.highfive.repository.forum.model.ForumRow;
import app.l2nx.highfive.repository.forum.model.PostRow;
import app.l2nx.highfive.repository.forum.model.TopicRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ForumsBBSManager extends BaseBBSManager {
    private static volatile ForumsBBSManager instance;

    private final Collection<Forum> table = ConcurrentHashMap.newKeySet();

    private final ForumRepository forumRepository;
    private final TopicRepository topicRepository;
    private final PostRepository postRepository;

    public ForumsBBSManager(
            ForumRepository forumRepository, TopicRepository topicRepository, PostRepository postRepository) {
        this.forumRepository = forumRepository;
        this.topicRepository = topicRepository;
        this.postRepository = postRepository;
        instance = this;
    }

    public void initRoot() {
        table.clear();
        try {
            for (int rootId : forumRepository.findRootIds()) {
                addForum(new Forum(rootId, null));
            }
        } catch (DataAccessException e) {
            log.warn("Data error on Forum (root): {}", e.getMessage(), e);
        }

        table.forEach(Forum::vload);
        log.info("Loaded {} forums.", table.size());
    }

    /**
     * Adds the forum.
     * @param ff the forum
     */
    public void addForum(Forum ff) {
        if (ff == null) {
            return;
        }

        table.add(ff);
    }

    @Override
    public void parsecmd(String command, Player player) {}

    /**
     * @return the forum by name
     */
    public Forum getForumByName(String name) {
        for (Forum forum : table) {
            if (name.equals(forum.getName())) {
                return forum;
            }
        }

        return null;
    }

    /**
     * Stores a forum owned by a clan (type {@link Forum#CLAN}) or a character (type {@link Forum#MAIL}).
     * @param ownerId the clan id or the character object id
     */
    public Forum createNewForum(Forum parent, int type, int perm, int ownerId) {
        return create(
                ForumRow.builder()
                        .parentId(parent.getID())
                        .type(type)
                        .permission(perm)
                        .ownerId(ownerId)
                        .build(),
                parent);
    }

    public Forum createMemoForum(Forum parent, UUID accountId) {
        return create(
                ForumRow.builder()
                        .parentId(parent.getID())
                        .type(Forum.MEMO)
                        .permission(Forum.OWNERONLY)
                        .accountId(accountId)
                        .build(),
                parent);
    }

    private Forum create(ForumRow row, Forum parent) {
        final int id = forumRepository.insert(row);
        return new Forum(row.toBuilder().id(id).build(), parent);
    }

    /**
     * Gets the forum by Id.
     * @param idf the the forum Id
     * @return the forum by Id
     */
    public Forum getForumByID(int idf) {
        for (Forum f : table) {
            if (f.getID() == idf) {
                return f;
            }
        }

        return null;
    }

    public Optional<ForumRow> findForum(int forumId) {
        return forumRepository.findById(forumId);
    }

    public List<Integer> findChildForumIds(int forumId) {
        return forumRepository.findChildIds(forumId);
    }

    /**
     * @return the forum's topics, newest first
     */
    public List<TopicRow> findTopics(int forumId) {
        return topicRepository.findByForum(forumId);
    }

    public int insertTopic(TopicRow topic) {
        return topicRepository.insert(topic);
    }

    /**
     * Deletes the topic with its posts.
     */
    public void deleteTopic(int topicId, int forumId) {
        topicRepository.delete(topicId, forumId);
    }

    /**
     * @return the topic's posts, oldest first
     */
    public List<PostRow> findPosts(int topicId) {
        return postRepository.findByTopic(topicId);
    }

    public long insertPost(PostRow post) {
        return postRepository.insert(post);
    }

    public void deletePosts(int topicId) {
        postRepository.deleteByTopic(topicId);
    }

    public void updatePostContent(long postId, String content) {
        postRepository.updateContent(postId, content);
    }

    @Override
    public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, Player player) {}

    // TODO: callers receive the bean through their constructor (#41).
    public static ForumsBBSManager getInstance() {
        final ForumsBBSManager current = instance;
        if (current == null) {
            throw new IllegalStateException("ForumsBBSManager is used before the Spring context created it.");
        }
        return current;
    }
}
