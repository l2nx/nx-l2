/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.ValueContext;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.ItemTransfer;
import app.l2nx.engine.mail.MailItemStore;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.Recipient;
import app.l2nx.engine.mail.Reservation;
import app.l2nx.engine.mail.ReservedItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * A player sends a mail: the receiver is checked and both mailboxes are locked inside the transaction, the fee leaves
 * the sender and the reserved items move into the mail.
 *
 * @param body {@code null} when the mail has none
 * @param codAdena {@code null} without cash on delivery
 * @param reservation the sender's items and the fee
 */
@Builder(toBuilder = true)
public record SendTransaction(
        MailWriter writer,
        UUID idempotencyKey,
        UUID senderId,
        String senderName,
        boolean senderGm,
        String receiverName,
        String subject,
        String body,
        Long codAdena,
        Reservation reservation)
        implements ValueTransaction<SendTransaction.Sent> {
    @Builder(toBuilder = true)
    public record Sent(UUID mailId, UUID receiverId) {}

    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_SEND;
    }

    @Override
    public UUID subjectId(Sent result) {
        return result.mailId();
    }

    @Override
    public Sent execute(ValueContext context) throws ValueRefusal {
        final MailItemStore store = writer.store;
        final MailRepository mails = writer.mails;
        final Recipient receiver =
                store.findRecipient(receiverName).orElseThrow(() -> new ValueRefusal(MailRefusal.RECEIVER_NOT_FOUND));
        if (receiver.id().equals(senderId)) {
            throw new ValueRefusal(MailRefusal.RECEIVER_SELF);
        }
        if (receiver.gm() && !senderGm) {
            throw new ValueRefusal(MailRefusal.RECEIVER_GM_ONLY, receiver.name());
        }
        if (store.isBlocked(receiver.id(), senderId)) {
            throw new ValueRefusal(MailRefusal.RECEIVER_BLOCKED, receiver.name());
        }
        mails.lockMailboxes(receiver.id(), senderId);
        if (mails.countReceived(receiver.id()) >= writer.settings.mailboxLimit()) {
            throw new ValueRefusal(MailRefusal.RECEIVER_MAILBOX_FULL);
        }
        if (mails.countSent(senderId) >= writer.settings.mailboxLimit()) {
            throw new ValueRefusal(MailRefusal.SENDER_MAILBOX_FULL);
        }

        final UUID mailId = EntityIds.next();
        final Instant now = context.now();
        mails.insert(MailRow.builder()
                .id(mailId)
                .type(MailType.PLAYER)
                .senderId(senderId)
                .senderName(senderName)
                .receiverId(receiver.id())
                .receiverName(receiver.name())
                .subject(subject)
                .body(body)
                .codAdena(codAdena)
                .attachments(reservation.items().isEmpty() ? AttachmentState.NONE : AttachmentState.ATTACHED)
                .sentAt(now)
                .expiresAt(now.plus(codAdena == null ? writer.settings.expiry() : writer.settings.codExpiry()))
                .build());
        writer.attachments.insertAll(mailId, reservation.snapshots());

        List<ReservedItem> items = reservation.items();
        if (reservation.adenaAmount() > 0) {
            final ItemTransfer fee =
                    store.adena(Holder.inventory(senderId), -reservation.adenaAmount(), reservation.adena());
            context.recordAll(fee.movements());
            items = afterAdenaChange(items, reservation.adena(), -reservation.adenaAmount());
        }
        if (!items.isEmpty()) {
            context.recordAll(store.toMail(mailId, items));
        }
        return Sent.builder().mailId(mailId).receiverId(receiver.id()).build();
    }

    // The store writes an online row's absolute count from its state, so the move of attached adena after the fee
    // must start from the count the fee left.
    private static List<ReservedItem> afterAdenaChange(List<ReservedItem> items, ItemRowState adena, long delta) {
        return items.stream()
                .map(item -> item.row().itemId().equals(adena.itemId())
                        ? new ReservedItem(item.row().withCount(item.row().count() + delta), item.count())
                        : item)
                .toList();
    }
}
