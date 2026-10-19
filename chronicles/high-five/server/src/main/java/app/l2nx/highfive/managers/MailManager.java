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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.network.serverpackets.ExNoticePostArrived;
import app.l2nx.highfive.taskmanagers.MessageDeletionTaskManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Migi, DS
 */
@Slf4j
public class MailManager {

    private final Map<Integer, MailMessage> messages = new ConcurrentHashMap<>();

    protected MailManager() {
        load();
    }

    private void load() {
        int count = 0;
        try (Connection con = DatabaseFactory.getConnection();
                Statement ps = con.createStatement();
                ResultSet rs = ps.executeQuery("SELECT * FROM messages ORDER BY expiration")) {
            while (rs.next()) {
                count++;
                final MailMessage msg = new MailMessage(rs);
                final int msgId = msg.getId();
                messages.put(msgId, msg);
                MessageDeletionTaskManager.getInstance().add(msgId, msg.getExpiration());
            }
        } catch (SQLException e) {
            log.warn("Error loading from database:", e);
        }

        log.info("Loaded {} messages.", count);
    }

    public MailMessage getMessage(int msgId) {
        return messages.get(msgId);
    }

    public Collection<MailMessage> getMessages() {
        return messages.values();
    }

    public boolean hasUnreadPost(Player player) {
        final int objectId = player.getObjectId();
        for (MailMessage msg : messages.values()) {
            if ((msg != null) && (msg.getReceiverId() == objectId) && msg.isUnread()) {
                return true;
            }
        }

        return false;
    }

    public int getInboxSize(int objectId) {
        int size = 0;
        for (MailMessage msg : messages.values()) {
            if ((msg != null) && (msg.getReceiverId() == objectId) && !msg.isDeletedByReceiver()) {
                size++;
            }
        }

        return size;
    }

    public int getOutboxSize(int objectId) {
        int size = 0;
        for (MailMessage msg : messages.values()) {
            if ((msg != null) && (msg.getSenderId() == objectId) && !msg.isDeletedBySender()) {
                size++;
            }
        }

        return size;
    }

    public List<MailMessage> getInbox(int objectId) {
        final List<MailMessage> inbox = new ArrayList<>();
        for (MailMessage msg : messages.values()) {
            if ((msg != null) && (msg.getReceiverId() == objectId) && !msg.isDeletedByReceiver()) {
                inbox.add(msg);
            }
        }

        return inbox;
    }

    public List<MailMessage> getOutbox(int objectId) {
        final List<MailMessage> outbox = new ArrayList<>();
        for (MailMessage msg : messages.values()) {
            if ((msg != null) && (msg.getSenderId() == objectId) && !msg.isDeletedBySender()) {
                outbox.add(msg);
            }
        }

        return outbox;
    }

    public void sendMessage(MailMessage msg) {
        messages.put(msg.getId(), msg);
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = MailMessage.getStatement(msg, con)) {
            ps.execute();
        } catch (SQLException e) {
            log.warn("Error saving message:", e);
        }

        final Player receiver = World.getPlayer(msg.getReceiverId());
        if (receiver != null) {
            receiver.sendPacket(ExNoticePostArrived.valueOf(true));
        }

        MessageDeletionTaskManager.getInstance().add(msg.getId(), msg.getExpiration());
    }

    public void markAsReadInDb(int msgId) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE messages SET isUnread = 'false' WHERE messageId = ?")) {
            ps.setInt(1, msgId);
            ps.execute();
        } catch (SQLException e) {
            log.warn("Error marking as read message:", e);
        }
    }

    public void markAsDeletedBySenderInDb(int msgId) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE messages SET isDeletedBySender = 'true' WHERE messageId = ?")) {
            ps.setInt(1, msgId);
            ps.execute();
        } catch (SQLException e) {
            log.warn("Error marking as deleted by sender message:", e);
        }
    }

    public void markAsDeletedByReceiverInDb(int msgId) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE messages SET isDeletedByReceiver = 'true' WHERE messageId = ?")) {
            ps.setInt(1, msgId);
            ps.execute();
        } catch (SQLException e) {
            log.warn("Error marking as deleted by receiver message:", e);
        }
    }

    public void removeAttachmentsInDb(int msgId) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE messages SET hasAttachments = 'false' WHERE messageId = ?")) {
            ps.setInt(1, msgId);
            ps.execute();
        } catch (SQLException e) {
            log.warn("Error removing attachments in message:", e);
        }
    }

    public void deleteMessageInDb(int msgId) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM messages WHERE messageId = ?")) {
            ps.setInt(1, msgId);
            ps.execute();
        } catch (SQLException e) {
            log.warn("Error deleting message:", e);
        }

        messages.remove(msgId);
        IdManager.getInstance().releaseId(msgId);
    }

    /**
     * Gets the single instance of {@code MailManager}.
     * @return single instance of {@code MailManager}
     */
    public static MailManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final MailManager INSTANCE = new MailManager();
    }
}
