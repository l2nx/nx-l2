/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Snapshots are shown whatever happened to the items; {@code attachments} tells whether they can still be taken.
 *
 * @param senderName {@code null} for system mail or a deleted sender
 * @param codAdenaPending {@code null} unless cash on delivery is still to be paid
 */
@Builder(toBuilder = true)
public record MailView(
        UUID mailId,
        MailType type,
        String senderName,
        String receiverName,
        String subject,
        String body,
        Long codAdenaPending,
        AttachmentState attachments,
        boolean unread,
        boolean returnable,
        boolean returned,
        List<AttachmentSnapshot> snapshots) {
    public MailView {
        snapshots = List.copyOf(snapshots);
    }
}
