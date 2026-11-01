/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

public enum MailChangeType {
    DELETED,
    READ,
    /** The attachments left the mail: the client clears its attachment icon. */
    ATTACHMENTS_TAKEN
}
