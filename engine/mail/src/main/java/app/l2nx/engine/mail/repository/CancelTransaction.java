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
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.ItemTransfer;
import app.l2nx.engine.mail.MailRefusal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * The sender takes the attachments of an unreceived mail back into its inventory.
 *
 * @param sender the sender's stacks the items merge into
 */
@Builder(toBuilder = true)
public record CancelTransaction(MailWriter writer, UUID idempotencyKey, UUID mailId, IncomingTargets sender)
        implements ValueTransaction<List<CommittedItemRow>> {
    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_CANCEL;
    }

    @Override
    public UUID subjectId(List<CommittedItemRow> result) {
        return mailId;
    }

    @Override
    public List<CommittedItemRow> execute(ValueContext context) throws ValueRefusal {
        if (!writer.mails.cancel(mailId, sender.characterId(), context.now())) {
            throw new ValueRefusal(MailRefusal.ALREADY_RECEIVED);
        }
        final ItemTransfer moved =
                writer.store.toCharacter(mailId, Holder.inventory(sender.characterId()), sender.stacks());
        context.recordAll(moved.movements());
        return moved.rows();
    }
}
