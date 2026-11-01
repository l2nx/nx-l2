/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.ValueContext;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.CreatedItems;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.Recipient;
import app.l2nx.engine.mail.SystemMail;
import java.time.Instant;
import java.util.UUID;

/** The system sends a mail: its items are created in the mail, with their snapshots, in the same transaction. */
public record SystemSendTransaction(MailWriter writer, SystemMail mail)
        implements ValueTransaction<SendTransaction.Sent> {
    @Override
    public ValueTransactionType type() {
        return ValueTransactionType.MAIL_SEND;
    }

    @Override
    public UUID idempotencyKey() {
        return mail.idempotencyKey();
    }

    @Override
    public UUID subjectId(SendTransaction.Sent result) {
        return result.mailId();
    }

    @Override
    public SendTransaction.Sent execute(ValueContext context) throws ValueRefusal {
        final Recipient receiver = writer.store
                .findRecipient(mail.receiverId())
                .orElseThrow(() -> new ValueRefusal(MailRefusal.RECEIVER_NOT_FOUND));
        final UUID mailId = EntityIds.next();
        final Instant now = context.now();
        writer.mails.insert(MailRow.builder()
                .id(mailId)
                .type(mail.type())
                .receiverId(receiver.id())
                .receiverName(receiver.name())
                .subject(mail.subject())
                .body(mail.body())
                .attachments(mail.items().isEmpty() ? AttachmentState.NONE : AttachmentState.ATTACHED)
                .sentAt(now)
                .expiresAt(now.plus(writer.settings.expiry()))
                .build());
        if (!mail.items().isEmpty()) {
            final CreatedItems created = writer.store.createInMail(mailId, mail.items());
            writer.attachments.insertAll(mailId, created.snapshots());
            context.recordAll(created.movements());
        }
        return SendTransaction.Sent.builder()
                .mailId(mailId)
                .receiverId(receiver.id())
                .build();
    }
}
