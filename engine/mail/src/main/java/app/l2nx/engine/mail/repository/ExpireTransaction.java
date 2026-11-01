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
import app.l2nx.engine.mail.ItemTransfer;
import app.l2nx.engine.mail.MailRefusal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * A due mail settles {@code EXPIRED}: its attachments go to the warehouse of their owner, or are destroyed when nobody
 * is left to get them.
 *
 * @param ownerId the owner the partition prepared for; a mail whose owner changed since is refused and expires on a
 *     later poll
 * @param warehouse the stacks of an online owner's warehouse, merged into by absolute counts; {@code null} for an owner
 *     not online, merged into by delta
 */
@Builder(toBuilder = true)
public record ExpireTransaction(
        MailWriter writer, UUID idempotencyKey, UUID mailId, UUID ownerId, IncomingTargets warehouse)
        implements ValueTransaction<ExpireTransaction.Expired> {
    /**
     * @param ownerId who got the attachments, {@code null} when nobody was left to get them
     * @param mail the mail as it was settled
     */
    @Builder(toBuilder = true)
    public record Expired(UUID ownerId, List<CommittedItemRow> rows, MailRow mail) {}

    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_EXPIRE;
    }

    @Override
    public UUID subjectId(Expired result) {
        return mailId;
    }

    @Override
    public Expired execute(ValueContext context) throws ValueRefusal {
        final MailRow mail = writer.mails
                .expire(mailId, context.now())
                .orElseThrow(() -> new ValueRefusal(MailRefusal.ALREADY_RECEIVED));
        final UUID owner = mail.expiryOwner();
        if (!Objects.equals(owner, ownerId)) {
            throw new ValueRefusal(MailRefusal.FAILED);
        }
        if (owner == null) {
            writer.destroy(context, mailId, AttachmentState.EXPIRED);
            return Expired.builder().rows(List.of()).mail(mail).build();
        }
        final ItemTransfer moved = writer.store.toCharacter(
                mailId, Holder.warehouse(owner), warehouse == null ? null : warehouse.stacks());
        context.recordAll(moved.movements());
        return Expired.builder().ownerId(owner).rows(moved.rows()).mail(mail).build();
    }
}
