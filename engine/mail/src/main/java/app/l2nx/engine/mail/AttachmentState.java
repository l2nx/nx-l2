/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

/** Where the attachments of a mail are; stored by name in {@code mails.attachments}. */
public enum AttachmentState {
    NONE,
    ATTACHED,
    RECEIVED,
    RETURNED,
    CANCELLED,
    EXPIRED,
    /** A claim found no live item left: nothing was moved and the cash on delivery was not charged. */
    DESTROYED
}
