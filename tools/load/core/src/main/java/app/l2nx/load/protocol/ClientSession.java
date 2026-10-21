/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol;

import app.l2nx.load.bot.intent.Intent;

/** The connection of one load bot; both methods are called on the session's event loop only. */
public interface ClientSession {
    /** Sends the intent; its acknowledgement comes later through {@link SessionListener#acknowledged}. */
    void send(Intent intent);

    /** Closes the connection without a logout; idempotent. */
    void close();
}
