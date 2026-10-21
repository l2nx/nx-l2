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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Mail;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExNoticePostArrived;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.repository.mail.MailRepository;
import app.l2nx.highfive.repository.mail.model.MailRow;
import app.l2nx.highfive.taskmanagers.MessageDeletionTaskManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Migi, DS
 */
@Slf4j
@Component
public class MailManager {
    private static volatile MailManager instance;

    private final Map<Integer, MailMessage> messages = new ConcurrentHashMap<>();

    private final MailRepository mailRepository;
    private final ItemRepository itemRepository;
    private final TransactionTemplate transactionTemplate;

    public MailManager(
            MailRepository mailRepository, ItemRepository itemRepository, TransactionTemplate transactionTemplate) {
        this.mailRepository = mailRepository;
        this.itemRepository = itemRepository;
        this.transactionTemplate = transactionTemplate;
        instance = this;
    }

    public void load() {
        int count = 0;
        for (MailRow row : mailRepository.findAll()) {
            count++;
            final MailMessage msg = new MailMessage(row);
            final int msgId = msg.getId();
            messages.put(msgId, msg);
            MessageDeletionTaskManager.getInstance().add(msgId, msg.getExpiration());
        }

        log.info("Loaded {} messages.", count);
    }

    public MailMessage getMessage(int msgId) {
        return messages.get(msgId);
    }

    public Collection<MailMessage> getMessages() {
        return messages.values();
    }

    /**
     * @return whether the mail is stored, so that its attachments may be written on their own
     */
    public boolean isSent(int msgId) {
        return messages.containsKey(msgId);
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

    /**
     * Stores the mail and its attachments in one transaction; until then the attachments live in memory only.
     */
    public void sendMessage(MailMessage msg) {
        messages.put(msg.getId(), msg);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                mailRepository.insert(msg.toRow());
                final Mail attachments = msg.getAttachments();
                if (attachments != null) {
                    attachments.updateDatabase();
                }
            });
        } catch (DataAccessException e) {
            log.warn("Error saving message:", e);
        }

        final Player receiver = World.getPlayer(msg.getReceiverId());
        if (receiver != null) {
            receiver.sendPacket(ExNoticePostArrived.valueOf(true));
        }

        MessageDeletionTaskManager.getInstance().add(msg.getId(), msg.getExpiration());
    }

    public void markAsReadInDb(int msgId) {
        try {
            mailRepository.markRead(msgId);
        } catch (DataAccessException e) {
            log.warn("Error marking as read message:", e);
        }
    }

    public void markAsDeletedBySenderInDb(int msgId) {
        try {
            mailRepository.markDeletedBySender(msgId);
        } catch (DataAccessException e) {
            log.warn("Error marking as deleted by sender message:", e);
        }
    }

    public void markAsDeletedByReceiverInDb(int msgId) {
        try {
            mailRepository.markDeletedByReceiver(msgId);
        } catch (DataAccessException e) {
            log.warn("Error marking as deleted by receiver message:", e);
        }
    }

    public void removeAttachmentsInDb(int msgId) {
        try {
            mailRepository.markAttachmentsRemoved(msgId);
        } catch (DataAccessException e) {
            log.warn("Error removing attachments in message:", e);
        }
    }

    public void deleteMessageInDb(int msgId) {
        try {
            mailRepository.delete(msgId);
        } catch (DataAccessException e) {
            log.warn("Error deleting message:", e);
        }

        forget(msgId);
    }

    /**
     * Moves a player mail's attachments to its sender's warehouse, as on expiry. A system mail has no sender: its
     * attachments stay with the receiver.
     */
    public void returnAttachments(MailMessage msg) {
        final Mail attachments = msg.getAttachments();
        if (attachments == null) {
            return;
        }

        final int ownerId = msg.getSenderId() == MailMessage.SYSTEM_SENDER ? msg.getReceiverId() : msg.getSenderId();
        final Player owner = World.getPlayer(ownerId);
        attachments.returnToWh(owner != null ? owner.getWarehouse() : null, ownerId);

        attachments.deleteMe();
        msg.removeAttachments();
    }

    /**
     * Runs inside the character-deletion transaction: attachments of incoming player mail return to their senders, all
     * other attachments and the mail are deleted. Memory changes only after commit; failures are thrown so no stored
     * mail keeps a deleted receiver.
     */
    public void deleteMailOfCharacter(int characterId) {
        transactionTemplate.executeWithoutResult(status -> {
            final List<MailMessage> deleted = new ArrayList<>();
            final List<Integer> deletedItems = new ArrayList<>();
            final Map<Integer, List<ItemRow>> returned = new HashMap<>();
            for (MailMessage msg : messages.values()) {
                final boolean received = msg.getReceiverId() == characterId;
                if (!received && (msg.getSenderId() != characterId)) {
                    continue;
                }

                if (received
                        && msg.hasAttachments()
                        && (msg.getSenderId() != MailMessage.SYSTEM_SENDER)
                        && (msg.getSenderId() != characterId)) {
                    returned.computeIfAbsent(msg.getSenderId(), sender -> new ArrayList<>())
                            .addAll(itemRepository.moveMailToWarehouse(msg.getId(), msg.getSenderId()));
                } else {
                    deletedItems.addAll(itemRepository.deleteByMail(msg.getId()));
                }

                mailRepository.delete(msg.getId());
                deleted.add(msg);
            }

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (MailMessage msg : deleted) {
                        msg.discardAttachments();
                        forget(msg.getId());
                    }

                    deletedItems.forEach(IdManager.getInstance()::releaseId);
                    returned.forEach(MailManager::addToOnlineWarehouse);
                }
            });
        });
    }

    /**
     * Shows returned items in the warehouse of an online owner; an offline owner reads them with the warehouse at the
     * next login.
     */
    private static void addToOnlineWarehouse(int ownerId, List<ItemRow> items) {
        final Player owner = World.getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        for (ItemRow row : items) {
            final Item item = Item.restoreFromDb(ownerId, row);
            if (item == null) {
                continue;
            }

            World.addObject(item);
            owner.getWarehouse().addItem(ItemProcessType.TRANSFER, item, null, null);
        }
    }

    private void forget(int msgId) {
        messages.remove(msgId);
        IdManager.getInstance().releaseId(msgId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static MailManager getInstance() {
        final MailManager current = instance;
        if (current == null) {
            throw new IllegalStateException("MailManager is used before the Spring context created it.");
        }
        return current;
    }
}
