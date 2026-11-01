/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.ValueContext;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.ItemTransfer;
import app.l2nx.engine.mail.MailItemStore;
import app.l2nx.engine.mail.MailRefusal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * The receiver takes the attachments and pays the cash on delivery to the sender. A mail whose items were all
 * destroyed outside mail settles {@code DESTROYED} without charging anything.
 *
 * @param senderId the sender as read with the mail, {@code null} when deleted
 * @param codAdena the cash on delivery as read with the mail, {@code null} for none
 * @param charge whether the cash on delivery is paid: it is not when the sender is gone
 * @param senderAdena the adena row of an online sender; {@code null} for an offline sender
 */
@Builder(toBuilder = true)
public record ReceiveTransaction(
        MailWriter writer,
        UUID idempotencyKey,
        UUID mailId,
        IncomingTargets receiver,
        UUID senderId,
        Long codAdena,
        boolean charge,
        ItemRowState senderAdena)
        implements ValueTransaction<ReceiveTransaction.Received> {
    @Builder(toBuilder = true)
    public record Received(
            AttachmentState attachments, List<CommittedItemRow> receiverRows, List<CommittedItemRow> senderRows) {}

    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_RECEIVE;
    }

    @Override
    public UUID subjectId(Received result) {
        return mailId;
    }

    @Override
    public Received execute(ValueContext context) throws ValueRefusal {
        final MailRepository mails = writer.mails;
        final MailItemStore store = writer.store;
        // The claim locks the row first, so a cancel or an expiry racing it waits and then finds it settled.
        if (!mails.claim(mailId, receiver.characterId(), senderId, codAdena, context.now())) {
            throw new ValueRefusal(MailRefusal.ALREADY_RECEIVED);
        }
        if (store.liveItemsInMail(mailId) == 0) {
            mails.destroy(mailId, AttachmentState.RECEIVED, context.now());
            return Received.builder()
                    .attachments(AttachmentState.DESTROYED)
                    .receiverRows(List.of())
                    .senderRows(List.of())
                    .build();
        }

        final List<CommittedItemRow> receiverRows = new ArrayList<>();
        final List<CommittedItemRow> senderRows = new ArrayList<>();
        List<ItemRowState> stacks = receiver.stacks();
        if (charge && (codAdena != null)) {
            final long cod = codAdena;
            final ItemTransfer paid = store.adena(Holder.inventory(receiver.characterId()), -cod, receiver.adena());
            context.recordAll(paid.movements());
            receiverRows.addAll(paid.rows());
            stacks = afterAdenaChange(stacks, receiver.adena(), -cod);
            final ItemTransfer earned = earned(store, cod);
            context.recordAll(earned.movements());
            senderRows.addAll(earned.rows());
        }
        final ItemTransfer moved = store.toCharacter(mailId, Holder.inventory(receiver.characterId()), stacks);
        context.recordAll(moved.movements());
        receiverRows.addAll(moved.rows());
        return Received.builder()
                .attachments(AttachmentState.RECEIVED)
                .receiverRows(receiverRows)
                .senderRows(senderRows)
                .build();
    }

    // The receiver is not the one at the limit; it learns why and may return the mail instead.
    private ItemTransfer earned(MailItemStore store, long cod) throws ValueRefusal {
        try {
            return store.adena(Holder.inventory(senderId), cod, senderAdena);
        } catch (ValueRefusal refusal) {
            if (refusal.reason() == MailRefusal.ADENA_LIMIT) {
                throw new ValueRefusal(MailRefusal.SENDER_ADENA_LIMIT);
            }
            throw refusal;
        }
    }

    // The store writes an online row's absolute count from its state, so a second write to the adena row in the same
    // transaction must start from the count the first one left.
    private static List<ItemRowState> afterAdenaChange(List<ItemRowState> rows, ItemRowState adena, long delta) {
        if (adena == null) {
            return rows;
        }
        return rows.stream()
                .map(row -> row.itemId().equals(adena.itemId()) ? row.withCount(row.count() + delta) : row)
                .toList();
    }
}
