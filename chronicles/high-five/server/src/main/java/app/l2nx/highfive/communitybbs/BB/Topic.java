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

import app.l2nx.highfive.communitybbs.Manager.TopicBBSManager;
import app.l2nx.highfive.communitybbs.TopicConstructorType;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Topic {

    public static final int NORMAL = 0;
    public static final int MEMO = 1;

    private final int id;

    @Getter
    private final int forumID;

    @Getter
    private final String name;

    @Getter
    private final long date;

    @Getter
    private final String ownerName;

    private final int ownerId;
    private final int type;
    private final int cReply;

    /**
     * @param ct
     * @param id
     * @param fid
     * @param name
     * @param date
     * @param oname
     * @param oid
     * @param type
     * @param cReply
     */
    public Topic(
            TopicConstructorType ct,
            int id,
            int fid,
            String name,
            long date,
            String oname,
            int oid,
            int type,
            int cReply) {
        this.id = id;
        forumID = fid;
        this.name = name;
        this.date = date;
        ownerName = oname;
        ownerId = oid;
        this.type = type;
        this.cReply = cReply;
        TopicBBSManager.getInstance().addTopic(this);

        if (ct == TopicConstructorType.CREATE) {
            insertindb();
        }
    }

    private void insertindb() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO topic (topic_id,topic_forum_id,topic_name,topic_date,topic_ownername,topic_ownerid,topic_type,topic_reply) values (?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, id);
            ps.setInt(2, forumID);
            ps.setString(3, name);
            ps.setLong(4, date);
            ps.setString(5, ownerName);
            ps.setInt(6, ownerId);
            ps.setInt(7, type);
            ps.setInt(8, cReply);
            ps.execute();
        } catch (Exception e) {
            log.warn("Error while saving new Topic to db {}", e.getMessage(), e);
        }
    }

    /**
     * @return the topic Id
     */
    public int getID() {
        return id;
    }

    /**
     * @param f
     */
    public void deleteme(Forum f) {
        TopicBBSManager.getInstance().delTopic(this);
        f.rmTopicByID(id);
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("DELETE FROM topic WHERE topic_id=? AND topic_forum_id=?")) {
            ps.setInt(1, id);
            ps.setInt(2, f.getID());
            ps.execute();
        } catch (Exception e) {
            log.warn("Error while deleting topic: {}", e.getMessage(), e);
        }
    }
}
