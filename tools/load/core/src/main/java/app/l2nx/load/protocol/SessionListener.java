/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.observation.Observation;

/** Callbacks of a session to its load bot, always on the session's event loop. */
public interface SessionListener {
    /** The lifecycle reached {@code state}; {@code STOPPED} before the load bot logged out means a dropped connection. */
    void stateChanged(LoadBotState state);

    void observed(Observation observation);

    /** The server confirmed the oldest pending intent of {@code kind}: acknowledgements are FIFO per kind. */
    void acknowledged(IntentKind kind);

    /** The server refused the oldest pending intent of {@code kind}, such as with L2 {@code ActionFailed}: an error, not a latency sample. */
    void rejected(IntentKind kind);

    void failed(Throwable cause);
}
