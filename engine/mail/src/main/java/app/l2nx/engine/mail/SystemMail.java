/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * A mail from the system (a script or the platform), sent once per {@code idempotencyKey}.
 *
 * @param body {@code null} when the mail has none
 */
@Builder(toBuilder = true)
public record SystemMail(
        UUID receiverId, MailType type, String subject, String body, List<NewItem> items, UUID idempotencyKey) {
    public SystemMail {
        Objects.requireNonNull(receiverId, "receiverId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (!type.isSystem()) {
            throw new IllegalArgumentException("A system mail cannot have the type " + type);
        }
        if ((subject == null) || subject.isBlank()) {
            throw new IllegalArgumentException("A system mail needs a subject");
        }
        if ((body != null) && body.isBlank()) {
            throw new IllegalArgumentException("A blank body is no body: pass null");
        }
        items = List.copyOf(items);
        if (items.size() > AttachmentSnapshot.MAX_SLOTS) {
            throw new IllegalArgumentException(
                    "At most " + AttachmentSnapshot.MAX_SLOTS + " attachments: " + items.size());
        }
    }
}
