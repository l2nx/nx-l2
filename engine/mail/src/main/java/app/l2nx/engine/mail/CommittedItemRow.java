/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import java.util.UUID;

/**
 * A committed item row of a character as the chronicle's store wrote it; the module hands it from the transaction to
 * {@link MailItems#applyIncoming} unread.
 */
public interface CommittedItemRow {
    UUID itemId();
}
