/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.MailSettings;
import org.springframework.stereotype.Component;

@Component
final class P267MailboxLimit {
    P267MailboxLimit(MailSettings settings) {
        final int max = P267MailWire.maxListMails();
        if (settings.mailboxLimit() > max) {
            throw new IllegalStateException("l2nx.mail.mailbox-limit " + settings.mailboxLimit() + " is above the "
                    + max + " mails a protocol 267 mail list frame holds");
        }
    }
}
