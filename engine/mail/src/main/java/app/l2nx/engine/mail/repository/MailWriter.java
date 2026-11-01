/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.ValueContext;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailItemStore;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the module's value transactions write through: the mail tables, the chronicle's item store and the settings a
 * new mail takes its expiry from. A caller hands it to the transactions it builds; only they reach into it, from the
 * runner's database transaction, so nothing here touches a game object.
 */
@Component
@RequiredArgsConstructor
public final class MailWriter {
    final MailSettings settings;
    final MailRepository mails;
    final MailAttachmentRepository attachments;
    final MailItemStore store;

    ReturnTransaction.Returned returnToSender(ValueContext context, UUID mailId, UUID receiverId) throws ValueRefusal {
        final Instant now = context.now();
        final MailRow original = mails.settleReturned(mailId, receiverId, now)
                .orElseThrow(() -> new ValueRefusal(MailRefusal.NOT_RETURNABLE));
        final UUID returnedId = EntityIds.next();
        // The returned mail goes from the one who returned it, hidden from that side's sent box at once.
        mails.insert(MailRow.builder()
                .id(returnedId)
                .type(original.type())
                .senderId(original.receiverId())
                .senderName(original.receiverName())
                .receiverId(original.senderId())
                .receiverName(original.senderName())
                .subject(original.subject())
                .body(original.body())
                .attachments(AttachmentState.ATTACHED)
                .returnedFromId(original.id())
                .sentAt(now)
                .expiresAt(now.plus(settings.expiry()))
                .senderDeletedAt(now)
                .build());
        attachments.copy(original.id(), returnedId);
        context.recordAll(store.toMailOf(original.id(), returnedId));
        return ReturnTransaction.Returned.builder()
                .mailId(returnedId)
                .senderId(original.senderId())
                .build();
    }

    /** Destroys the live items of a mail nobody can get any more and settles it {@code DESTROYED}. */
    void destroy(ValueContext context, UUID mailId, AttachmentState from) throws ValueRefusal {
        if (!mails.destroy(mailId, from, context.now())) {
            throw new ValueRefusal(MailRefusal.FAILED);
        }
        context.recordAll(store.destroyInMail(mailId));
    }
}
