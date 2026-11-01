/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.MailSummary;
import app.l2nx.engine.mail.view.MailView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * A row of {@code mails}; the nullable columns are {@code null} here too.
 *
 * @param senderId {@code null} for system mail or a deleted sender
 * @param senderName {@code null} for system mail
 * @param receiverId {@code null} for a deleted receiver
 */
@Builder(toBuilder = true)
public record MailRow(
        UUID id,
        MailType type,
        UUID senderId,
        String senderName,
        UUID receiverId,
        String receiverName,
        String subject,
        String body,
        Long codAdena,
        AttachmentState attachments,
        UUID returnedFromId,
        Instant sentAt,
        Instant expiresAt,
        Instant readAt,
        Instant settledAt,
        Instant senderDeletedAt,
        Instant receiverDeletedAt,
        Instant expiredAt) {
    public boolean attached() {
        return attachments == AttachmentState.ATTACHED;
    }

    public boolean returned() {
        return returnedFromId != null;
    }

    /** The receiver may send the attachments back: a player's mail still holding them, not itself a returned one. */
    public boolean returnable() {
        return attached() && (type == MailType.PLAYER) && !returned() && (senderId != null);
    }

    /** Cash on delivery still to be paid; {@code null} when there is none. */
    public Long codPending() {
        return attached() ? codAdena : null;
    }

    /**
     * Who the attachments came from: the sender, or for a returned mail its receiver, who sent the original; {@code
     * null} for system mail and when that character was deleted.
     */
    public UUID originatorId() {
        if (type.isSystem()) {
            return null;
        }
        return returned() ? receiverId : senderId;
    }

    /**
     * Who gets the attachments when the mail expires: the character they came from, else the receiver (system mail, a
     * deleted sender); {@code null} when nobody is left. A returned mail whose original sender was deleted has nobody:
     * the one who returned it gave the items up.
     */
    public UUID expiryOwner() {
        if (returned()) {
            return receiverId;
        }
        final UUID originator = originatorId();
        return originator != null ? originator : receiverId;
    }

    public MailSummary receivedSummary() {
        return summary(senderName);
    }

    public MailSummary sentSummary() {
        return summary(receiverName);
    }

    /** @param unread whether the receiver has not read it yet, as it stands after this opening */
    public MailView view(List<AttachmentSnapshot> snapshots, boolean unread) {
        return MailView.builder()
                .mailId(id)
                .type(type)
                .senderName(senderName)
                .receiverName(receiverName)
                .subject(subject)
                .body(body)
                .codAdenaPending(codPending())
                .attachments(attachments)
                .unread(unread)
                .returnable(returnable())
                .returned(returned())
                .snapshots(snapshots)
                .build();
    }

    private MailSummary summary(String counterpartName) {
        return MailSummary.builder()
                .mailId(id)
                .type(type)
                .counterpartName(counterpartName)
                .subject(subject)
                .expiresAt(expiresAt)
                .unread(readAt == null)
                .codPending(codPending() != null)
                .attachmentsTakeable(attached())
                .returnable(returnable())
                .returned(returned())
                .build();
    }
}
