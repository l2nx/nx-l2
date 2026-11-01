/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

public enum MailNotice {
    SENT,
    RECEIVED,
    RETURNED,
    CANCELLED,
    /** To the sender, naming the receiver who took the attachments of a mail without cash on delivery. */
    ATTACHMENTS_TAKEN,
    /** To the sender, naming the payer and the cash on delivery paid. */
    COD_PAID,
    /** To the original sender, naming the receiver who returned the mail. */
    RETURNED_TO_YOU,
    /** To the receiver, naming the sender who cancelled the mail. */
    CANCELLED_BY_SENDER,
    /** To the sender of a mail whose attachments came back on expiry. */
    EXPIRED_RETURNED
}
