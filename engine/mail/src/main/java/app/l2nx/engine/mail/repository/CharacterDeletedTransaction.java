/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.core.persistence.value.ValueContext;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.mail.AttachmentState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Settles the mail of a character about to be deleted: a player's mail it received goes back to its living sender, any
 * other received attachments are destroyed, and the cash on delivery of its sent mail is dropped. Returns the returned mails.
 */
@Builder(toBuilder = true)
public record CharacterDeletedTransaction(MailWriter writer, UUID idempotencyKey, UUID characterId)
        implements ValueTransaction<List<ReturnTransaction.Returned>> {
    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_CHARACTER_DELETE;
    }

    @Override
    public List<ReturnTransaction.Returned> execute(ValueContext context) throws ValueRefusal {
        writer.mails.dropCod(characterId);
        final List<ReturnTransaction.Returned> returned = new ArrayList<>();
        for (MailRow mail : writer.mails.lockAttachedReceived(characterId)) {
            if (mail.returnable()) {
                returned.add(writer.returnToSender(context, mail.id(), characterId));
            } else {
                writer.destroy(context, mail.id(), AttachmentState.ATTACHED);
            }
        }
        return returned;
    }
}
