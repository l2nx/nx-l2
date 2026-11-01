/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.mail.MailType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * @param counterpartName the sender in the received list, the receiver in the sent list; {@code null} for system
 *     mail or a deleted sender
 */
@Builder(toBuilder = true)
public record MailSummary(
        UUID mailId,
        MailType type,
        String counterpartName,
        String subject,
        Instant expiresAt,
        boolean unread,
        boolean codPending,
        boolean attachmentsTakeable,
        boolean returnable,
        boolean returned) {}
