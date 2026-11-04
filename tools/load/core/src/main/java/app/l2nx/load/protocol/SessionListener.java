/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.observation.Observation;

public interface SessionListener {
    /**
     * {@code STOPPED} before logout means a dropped connection. The acknowledgement of the intent that led here must
     * come first: a load bot closes on its final state and counts what is still pending as timed out.
     */
    void stateChanged(LoadBotState state);

    void observed(Observation observation);

    void acknowledged(IntentKind kind);

    /**
     * An answer matching no pending intent is dropped: its intent is gone, or it is a repeat nobody waits for.
     */
    void acknowledged(IntentKind kind, Object correlation);

    /** Refuses the oldest pending intent of {@code kind}: an error, not a latency sample. */
    void rejected(IntentKind kind);

    void failed(Throwable cause);

    /** One inbound frame was handled, {@code nanos} after {@code channelRead} of the bytes that carried it. */
    default void frameHandled(long nanos) {}
}
