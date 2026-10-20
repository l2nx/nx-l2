/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.mail.model;

import app.l2nx.highfive.network.enums.MessageSenderType;
import java.time.Instant;
import lombok.Builder;

/**
 * @param senderId the sender handle, {@code -1} for a mail sent by the system
 * @param subject {@code null} for none (return mails have no subject)
 * @param content {@code null} for none
 * @param reqAdena the cash-on-delivery price, {@code 0} for none
 */
@Builder(toBuilder = true)
public record MailRow(
        int id,
        int senderId,
        int receiverId,
        String subject,
        String content,
        Instant expiration,
        long reqAdena,
        boolean hasAttachments,
        boolean unread,
        boolean deletedBySender,
        boolean deletedByReceiver,
        MessageSenderType senderType,
        boolean returned) {}
