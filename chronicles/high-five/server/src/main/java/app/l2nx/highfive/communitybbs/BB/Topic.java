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

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.communitybbs.Manager.TopicBBSManager;
import app.l2nx.highfive.repository.forum.model.TopicRow;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

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

    public Topic(TopicRow row) {
        id = row.id();
        forumID = row.forumId();
        name = row.title();
        date = epochMillisOrZero(row.createdAt());
        ownerName = row.ownerName();
        TopicBBSManager.getInstance().addTopic(this);
    }

    /**
     * @param fid
     * @param name
     * @param date
     * @param oname
     * @param oid
     * @param type
     */
    public Topic(int fid, String name, long date, String oname, int oid, int type) {
        forumID = fid;
        this.name = name;
        this.date = date;
        ownerName = oname;
        id = ForumsBBSManager.getInstance()
                .insertTopic(TopicRow.builder()
                        .forumId(fid)
                        .title(name)
                        .createdAt(instantOrNull(date))
                        .ownerId(oid)
                        .type(type)
                        .build());
        TopicBBSManager.getInstance().addTopic(this);
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
        try {
            ForumsBBSManager.getInstance().deleteTopic(id, f.getID());
        } catch (DataAccessException e) {
            log.warn("Error while deleting topic: {}", e.getMessage(), e);
        }
    }
}
