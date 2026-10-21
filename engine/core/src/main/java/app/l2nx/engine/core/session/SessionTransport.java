/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.network.channel.CloseReason;

public interface SessionTransport {
    /** Runs the task on the connection's IO thread, the only producer of the session queue. */
    void execute(Runnable task);

    /**
     * Called in transition order while the session holds its transition lock: it must not wait for another thread
     * that changes the state of the same session.
     */
    void stateChanged(SessionState from, SessionState to);

    /** Any thread. */
    void close(CloseReason reason);
}
