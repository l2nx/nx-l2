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
import app.l2nx.highfive.communitybbs.Manager.TopicBBSManager;
import app.l2nx.highfive.communitybbs.TopicConstructorType;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

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
    private int forumPost;
    private int forumPerm;
    private final Forum fParent;
    private int ownerID;
    private boolean loaded = false;

    /**
     * Creates new instance of Forum. When you create new forum, use {@link app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager#addForum(app.l2nx.highfive.communitybbs.BB.Forum)} to add forum to the forums manager.
     * @param forumId
     * @param fParent
     */
    public Forum(int forumId, Forum fParent) {
        id = forumId;
        this.fParent = fParent;
        children = ConcurrentHashMap.newKeySet();
    }

    /**
     * @param name
     * @param parent
     * @param type
     * @param perm
     * @param ownerId
     */
    public Forum(String name, Forum parent, int type, int perm, int ownerId) {
        forumName = name;
        id = ForumsBBSManager.getInstance().getANewID();
        forumType = type;
        forumPost = 0;
        forumPerm = perm;
        fParent = parent;
        ownerID = ownerId;
        children = ConcurrentHashMap.newKeySet();
        parent.children.add(this);
        ForumsBBSManager.getInstance().addForum(this);
        loaded = true;
    }

    private void load() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM forums WHERE forum_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    forumName = rs.getString("forum_name");
                    forumPost = rs.getInt("forum_post");
                    forumType = rs.getInt("forum_type");
                    forumPerm = rs.getInt("forum_perm");
                    ownerID = rs.getInt("forum_owner_id");
                }
            }
        } catch (Exception e) {
            log.warn("Data error on Forum {} : {}", id, e.getMessage(), e);
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT * FROM topic WHERE topic_forum_id=? ORDER BY topic_id DESC")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final Topic t = new Topic(
                            TopicConstructorType.RESTORE,
                            rs.getInt("topic_id"),
                            rs.getInt("topic_forum_id"),
                            rs.getString("topic_name"),
                            rs.getLong("topic_date"),
                            rs.getString("topic_ownername"),
                            rs.getInt("topic_ownerid"),
                            rs.getInt("topic_type"),
                            rs.getInt("topic_reply"));
                    topic.put(t.getID(), t);
                    if (t.getID() > TopicBBSManager.getInstance().getMaxID(this)) {
                        TopicBBSManager.getInstance().setMaxID(t.getID(), this);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Data error on Forum {} : {}", id, e.getMessage(), e);
        }
    }

    private void getChildren() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT forum_id FROM forums WHERE forum_parent=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final Forum f = new Forum(rs.getInt("forum_id"), this);
                    children.add(f);
                    ForumsBBSManager.getInstance().addForum(f);
                }
            }
        } catch (Exception e) {
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

    public String getName() {
        vload();
        return forumName;
    }

    public int getType() {
        vload();
        return forumType;
    }

    /**
     * @param name the forum name
     * @return the forum for the given name
     */
    public Forum getChildByName(String name) {
        vload();
        for (Forum forum : children) {
            if (forum.getName().equals(name)) {
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

    public void insertIntoDb() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO forums (forum_id,forum_name,forum_parent,forum_post,forum_type,forum_perm,forum_owner_id) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, id);
            ps.setString(2, forumName);
            ps.setInt(3, fParent.getID());
            ps.setInt(4, forumPost);
            ps.setInt(5, forumType);
            ps.setInt(6, forumPerm);
            ps.setInt(7, ownerID);
            ps.execute();
        } catch (Exception e) {
            log.warn("Error while saving new Forum to db {}", e.getMessage(), e);
        }
    }

    public void vload() {
        if (!loaded) {
            load();
            getChildren();
            loaded = true;
        }
    }
}
