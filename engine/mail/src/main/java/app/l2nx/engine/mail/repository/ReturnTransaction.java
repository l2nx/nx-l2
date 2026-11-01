/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.core.persistence.value.ValueContext;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import java.util.UUID;
import lombok.Builder;

/** The receiver sends the attachments back to the sender in a new mail; only mail rows move. */
@Builder(toBuilder = true)
public record ReturnTransaction(MailWriter writer, UUID idempotencyKey, UUID mailId, UUID receiverId)
        implements ValueTransaction<ReturnTransaction.Returned> {
    @Builder(toBuilder = true)
    public record Returned(UUID mailId, UUID senderId) {}

    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_RETURN;
    }

    @Override
    public UUID subjectId(Returned result) {
        return result.mailId();
    }

    @Override
    public Returned execute(ValueContext context) throws ValueRefusal {
        return writer.returnToSender(context, mailId, receiverId);
    }
}
