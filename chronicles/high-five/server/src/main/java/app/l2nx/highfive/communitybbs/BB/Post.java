/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.communitybbs.BB;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.communitybbs.Manager.PostBBSManager;
import app.l2nx.highfive.repository.forum.model.PostRow;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author Maktakien
 */
@Slf4j
public class Post {

    public static class CPost {
        @Getter
        @Setter
        private long postId;

        @Getter
        @Setter
        private String postOwner;

        @Getter
        @Setter
        private int postOwnerId;

        @Getter
        @Setter
        private long postDate;

        @Getter
        @Setter
        private int postTopicId;

        @Getter
        @Setter
        private int postForumId;

        @Setter
        private String postText;

        public String getPostText() {
            if (postText == null) {
                return "";
            }

            // Bypass exploit check.
            final String text = postText.toLowerCase();
            if (text.contains("action") && text.contains("bypass")) {
                return "";
            }

            // Returns text without tags.
            return postText.replaceAll("<.*?>", "");
        }
    }

    // Oldest first; immutable, so safe to share between threads.
    private final List<CPost> post;

    /**
     * @param postOwner
     * @param postOwnerId
     * @param date
     * @param tid
     * @param postForumId
     * @param txt
     */
    public Post(String postOwner, int postOwnerId, long date, int tid, int postForumId, String txt) {
        final CPost cp = new CPost();
        cp.setPostOwner(postOwner);
        cp.setPostOwnerId(postOwnerId);
        cp.setPostDate(date);
        cp.setPostTopicId(tid);
        cp.setPostForumId(postForumId);
        cp.setPostText(txt);
        cp.setPostId(ForumsBBSManager.getInstance()
                .insertPost(PostRow.builder()
                        .topicId(tid)
                        .ownerId(postOwnerId)
                        .createdAt(instantOrNull(date))
                        .content(cp.getPostText())
                        .build()));
        post = List.of(cp);
    }

    public Post(Topic t) {
        post = load(t);
    }

    public CPost getCPost(int index) {
        return (index >= 0) && (index < post.size()) ? post.get(index) : null;
    }

    public void deleteMe(Topic t) {
        PostBBSManager.getInstance().delPostByTopic(t);
        try {
            ForumsBBSManager.getInstance().deletePosts(t.getID());
        } catch (DataAccessException e) {
            log.warn("Error while deleting post: {}", e.getMessage(), e);
        }
    }

    private static List<CPost> load(Topic t) {
        final List<CPost> posts = new ArrayList<>();
        try {
            // findPosts returns the posts by ascending identity id, which is their creation order.
            for (PostRow row : ForumsBBSManager.getInstance().findPosts(t.getID())) {
                final CPost cp = new CPost();
                cp.setPostId(row.id());
                cp.setPostOwner(row.ownerName());
                cp.setPostOwnerId(row.ownerId());
                cp.setPostDate(epochMillisOrZero(row.createdAt()));
                cp.setPostTopicId(row.topicId());
                cp.setPostForumId(t.getForumID());
                cp.setPostText(row.content());
                posts.add(cp);
            }
        } catch (DataAccessException e) {
            log.warn("Data error on Post {}/{} : {}", t.getForumID(), t.getID(), e.getMessage(), e);
        }
        return List.copyOf(posts);
    }

    public void updateText(int i) {
        final CPost cp = getCPost(i);
        try {
            ForumsBBSManager.getInstance().updatePostContent(cp.getPostId(), cp.getPostText());
        } catch (DataAccessException e) {
            log.warn("Error while saving new Post to db {}", e.getMessage(), e);
        }
    }
}
