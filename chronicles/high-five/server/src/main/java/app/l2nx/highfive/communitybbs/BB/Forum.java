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
package app.l2nx.highfive.communitybbs.BB;

import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.repository.forum.model.ForumRow;
import app.l2nx.highfive.repository.forum.model.TopicRow;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class Forum {

    // type
    public static final int ROOT = 0;
    public static final int NORMAL = 1;
    public static final int CLAN = 2;
    public static final int MEMO = 3;
    public static final int MAIL = 4;

    // perm
    public static final int INVISIBLE = 0;
    public static final int ALL = 1;
    public static final int CLANMEMBERONLY = 2;
    public static final int OWNERONLY = 3;

    private final Collection<Forum> children;
    private final Map<Integer, Topic> topic = new ConcurrentHashMap<>();
    private final int id;
    private String forumName;
    private int forumType;
    private int forumPerm;
    private final Forum fParent;
    private int ownerID;
    private UUID accountId;
    private boolean loaded = false;

    /**
     * Creates new instance of Forum. When you create new forum, use {@link ForumsBBSManager#addForum(Forum)} to add
     * forum to the forums manager.
     * @param forumId
     * @param fParent
     */
    public Forum(int forumId, Forum fParent) {
        id = forumId;
        this.fParent = fParent;
        children = ConcurrentHashMap.newKeySet();
    }

    /**
     * A forum just stored by {@link ForumsBBSManager#createNewForum}; it has no topics yet.
     */
    public Forum(ForumRow row, Forum parent) {
        id = row.id();
        forumName = row.name();
        forumType = row.type();
        forumPerm = row.permission();
        fParent = parent;
        ownerID = row.ownerId();
        accountId = row.accountId();
        children = ConcurrentHashMap.newKeySet();
        parent.children.add(this);
        ForumsBBSManager.getInstance().addForum(this);
        loaded = true;
    }

    private void load() {
        try {
            ForumsBBSManager.getInstance().findForum(id).ifPresent(row -> {
                forumName = row.name();
                forumType = row.type();
                forumPerm = row.permission();
                ownerID = row.ownerId();
                accountId = row.accountId();
            });
        } catch (DataAccessException e) {
            log.warn("Data error on Forum {} : {}", id, e.getMessage(), e);
        }

        try {
            for (TopicRow row : ForumsBBSManager.getInstance().findTopics(id)) {
                final Topic t = new Topic(row);
                topic.put(t.getID(), t);
            }
        } catch (DataAccessException e) {
            log.warn("Data error on Forum {} : {}", id, e.getMessage(), e);
        }
    }

    private void getChildren() {
        try {
            for (int childId : ForumsBBSManager.getInstance().findChildForumIds(id)) {
                final Forum f = new Forum(childId, this);
                children.add(f);
                ForumsBBSManager.getInstance().addForum(f);
            }
        } catch (DataAccessException e) {
            log.warn("Data error on Forum (children): {}", e.getMessage(), e);
        }
    }

    public int getTopicSize() {
        vload();
        return topic.size();
    }

    public Topic getTopic(int j) {
        vload();
        return topic.get(j);
    }

    public Collection<Topic> getTopics() {
        vload();
        return topic.values();
    }

    public void addTopic(Topic t) {
        vload();
        topic.put(t.getID(), t);
    }

    /**
     * @return the forum Id
     */
    public int getID() {
        return id;
    }

    /**
     * @return the name of a root or normal forum; {@code null} for a forum owned by a clan, character or account
     */
    public String getName() {
        vload();
        return forumName;
    }

    public int getType() {
        vload();
        return forumType;
    }

    /**
     * @param ownerId the clan id of a clan forum or the character object id of a mail forum
     */
    public Forum getChildByOwner(int ownerId) {
        vload();
        for (Forum forum : children) {
            final int type = forum.getType();
            if (((type == CLAN) || (type == MAIL)) && (forum.ownerID == ownerId)) {
                return forum;
            }
        }

        return null;
    }

    public Forum getChildByAccount(UUID accountId) {
        vload();
        for (Forum forum : children) {
            if ((forum.getType() == MEMO) && accountId.equals(forum.accountId)) {
                return forum;
            }
        }

        return null;
    }

    /**
     * @param id
     */
    public void rmTopicByID(int id) {
        topic.remove(id);
    }

    public void vload() {
        if (!loaded) {
            load();
            getChildren();
            loaded = true;
        }
    }
}
