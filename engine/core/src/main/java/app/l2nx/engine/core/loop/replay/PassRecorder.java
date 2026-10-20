/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.replay;

import app.l2nx.engine.core.loop.Message;

/**
 * Inputs of a loop in the order it processed them, so that a replay against the same build reproduces it. Called
 * on the owner thread only.
 */
public interface PassRecorder {
    PassRecorder NOOP = new PassRecorder() {};

    default void loopStarted(String loop, long seed, long gameMillis, long wallMillis) {}

    /** {@code drain}: a pass of the shutdown drain, which runs no deadlines and no periodic systems. */
    default void passStarted(long pass, long gameMillis, long wallMillis, boolean drain) {}

    default void message(long pass, int order, Message message) {}

    /** One item taken from an input source, recorded before it runs, so an item that fails is recorded too. */
    default void inputItem(long pass, int order, long sourceId, Message item) {}
}
