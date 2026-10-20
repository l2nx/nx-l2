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
package app.l2nx.highfive.network.holders;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.itemcontainer.Mail;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.network.enums.MessageSenderType;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Migi, DS
 */
public class MailMessage {
    private static final int EXPIRATION = 360; // 15 days
    private static final int COD_EXPIRATION = 12; // 12 hours

    private static final int UNLOAD_ATTACHMENTS_INTERVAL = 900000; // 15-30 mins

    // post state
    public static final int DELETED = 0;
    public static final int READED = 1;
    public static final int REJECTED = 2;

    @Getter
    private final int id;

    @Getter
    private final int senderId;

    @Getter
    private final int receiverId;

    @Getter
    private final long expiration;

    private String senderName = null;
    private String receiverName = null;

    @Getter
    private final String subject;

    @Getter
    private final String content;

    @Getter
    private boolean unread;

    @Getter
    @Setter
    private boolean returned;

    @Getter
    private int sendBySystem;

    @Getter
    private boolean deletedBySender;

    @Getter
    private boolean deletedByReceiver;

    @Getter
    private final long reqAdena;

    private boolean hasAttachments;
    private Mail attachments = null;
    private ScheduledFuture<?> unloadTask = null;

    /*
     * Constructor for restoring from DB.
     */
    public MailMessage(ResultSet rset) throws SQLException {
        id = rset.getInt("messageId");
        senderId = rset.getInt("senderId");
        receiverId = rset.getInt("receiverId");
        subject = rset.getString("subject");
        content = rset.getString("content");
        expiration = rset.getLong("expiration");
        reqAdena = rset.getLong("reqAdena");
        hasAttachments = Boolean.parseBoolean(rset.getString("hasAttachments"));
        unread = Boolean.parseBoolean(rset.getString("isUnread"));
        deletedBySender = Boolean.parseBoolean(rset.getString("isDeletedBySender"));
        deletedByReceiver = Boolean.parseBoolean(rset.getString("isDeletedByReceiver"));
        sendBySystem = rset.getInt("sendBySystem");
        returned = Boolean.parseBoolean(rset.getString("isReturned"));
    }

    /*
     * This constructor used for creating new message.
     */
    public MailMessage(int senderId, int receiverId, boolean isCod, String subject, String text, long reqAdena) {
        id = IdManager.getInstance().getNextId();
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.subject = subject;
        content = text;
        expiration = isCod
                ? ClockBridge.wallMillis() + (COD_EXPIRATION * 3600000)
                : ClockBridge.wallMillis() + (EXPIRATION * 3600000);
        hasAttachments = false;
        unread = true;
        deletedBySender = false;
        deletedByReceiver = false;
        this.reqAdena = reqAdena;
    }

    /*
     * This constructor used for System Mails
     */
    public MailMessage(int receiverId, String subject, String content, MessageSenderType sendBySystem) {
        id = IdManager.getInstance().getNextId();
        senderId = -1;
        this.receiverId = receiverId;
        this.subject = subject;
        this.content = content;
        expiration = ClockBridge.wallMillis() + (EXPIRATION * 3600000);
        reqAdena = 0;
        hasAttachments = false;
        unread = true;
        deletedBySender = true;
        deletedByReceiver = false;
        this.sendBySystem = sendBySystem.ordinal();
        returned = false;
    }

    /*
     * This constructor used for auto-generation of the "return attachments" message
     */
    public MailMessage(MailMessage msg) {
        id = IdManager.getInstance().getNextId();
        senderId = msg.getSenderId();
        receiverId = msg.getSenderId();
        subject = "";
        content = "";
        expiration = ClockBridge.wallMillis() + (EXPIRATION * 3600000);
        unread = true;
        deletedBySender = true;
        deletedByReceiver = false;
        sendBySystem = MessageSenderType.NONE.ordinal();
        returned = true;
        reqAdena = 0;
        hasAttachments = true;
        attachments = msg.getAttachments();
        msg.removeAttachments();
        attachments.setNewMessageId(id);
        unloadTask = ThreadPool.schedule(
                new AttachmentsUnloadTask(this),
                UNLOAD_ATTACHMENTS_INTERVAL + ClockBridge.random().nextInt(UNLOAD_ATTACHMENTS_INTERVAL));
    }

    public static PreparedStatement getStatement(MailMessage msg, Connection con) throws SQLException {
        final PreparedStatement stmt = con.prepareStatement(
                "INSERT INTO messages (messageId, senderId, receiverId, subject, content, expiration, reqAdena, hasAttachments, isUnread, isDeletedBySender, isDeletedByReceiver, sendBySystem, isReturned) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        stmt.setInt(1, msg.id);
        stmt.setInt(2, msg.senderId);
        stmt.setInt(3, msg.receiverId);
        stmt.setString(4, msg.subject);
        stmt.setString(5, msg.content);
        stmt.setLong(6, msg.expiration);
        stmt.setLong(7, msg.reqAdena);
        stmt.setString(8, String.valueOf(msg.hasAttachments));
        stmt.setString(9, String.valueOf(msg.unread));
        stmt.setString(10, String.valueOf(msg.deletedBySender));
        stmt.setString(11, String.valueOf(msg.deletedByReceiver));
        stmt.setString(12, String.valueOf(msg.sendBySystem));
        stmt.setString(13, String.valueOf(msg.returned));
        return stmt;
    }

    public String getSenderName() {
        if (senderName == null) {
            if (sendBySystem != 0) {
                return "****";
            }

            senderName = CharInfoTable.getInstance().getNameById(senderId);
            if (senderName == null) {
                senderName = "";
            }
        }

        return senderName;
    }

    public String getReceiverName() {
        if (receiverName == null) {
            receiverName = CharInfoTable.getInstance().getNameById(receiverId);
            if (receiverName == null) {
                receiverName = "";
            }
        }

        return receiverName;
    }

    public boolean isLocked() {
        return reqAdena > 0;
    }

    public int getExpirationSeconds() {
        return (int) (expiration / 1000);
    }

    public void markAsRead() {
        if (!unread) {
            return;
        }

        unread = false;
        MailManager.getInstance().markAsReadInDb(id);
    }

    public void setDeletedBySender() {
        if (deletedBySender) {
            return;
        }

        deletedBySender = true;
        if (deletedByReceiver) {
            MailManager.getInstance().deleteMessageInDb(id);
        } else {
            MailManager.getInstance().markAsDeletedBySenderInDb(id);
        }
    }

    public void setDeletedByReceiver() {
        if (deletedByReceiver) {
            return;
        }

        deletedByReceiver = true;
        if (deletedBySender) {
            MailManager.getInstance().deleteMessageInDb(id);
        } else {
            MailManager.getInstance().markAsDeletedByReceiverInDb(id);
        }
    }

    public synchronized Mail getAttachments() {
        if (!hasAttachments) {
            return null;
        }

        if (attachments == null) {
            attachments = new Mail(senderId, id);
            attachments.restore();
            unloadTask = ThreadPool.schedule(
                    new AttachmentsUnloadTask(this),
                    UNLOAD_ATTACHMENTS_INTERVAL + ClockBridge.random().nextInt(UNLOAD_ATTACHMENTS_INTERVAL));
        }

        return attachments;
    }

    public boolean hasAttachments() {
        return hasAttachments;
    }

    public synchronized void removeAttachments() {
        if (attachments == null) {
            return;
        }

        attachments = null;
        hasAttachments = false;
        MailManager.getInstance().removeAttachmentsInDb(id);
        if (unloadTask != null) {
            unloadTask.cancel(false);
        }
    }

    public synchronized Mail createAttachments() {
        if (hasAttachments || (attachments != null)) {
            return null;
        }

        attachments = new Mail(senderId, id);
        hasAttachments = true;
        unloadTask = ThreadPool.schedule(
                new AttachmentsUnloadTask(this),
                UNLOAD_ATTACHMENTS_INTERVAL + ClockBridge.random().nextInt(UNLOAD_ATTACHMENTS_INTERVAL));
        return attachments;
    }

    protected synchronized void unloadAttachments() {
        if (attachments == null) {
            return;
        }

        attachments.deleteMe();
        attachments = null;
    }

    private static class AttachmentsUnloadTask implements Runnable {
        private MailMessage msg;

        AttachmentsUnloadTask(MailMessage msg) {
            this.msg = msg;
        }

        @Override
        public void run() {
            if (msg == null) {
                return;
            }

            msg.unloadAttachments();
            msg = null;
        }
    }
}
