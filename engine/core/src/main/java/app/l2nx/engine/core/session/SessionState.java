/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

/**
 * Lifecycle of a client connection. Whether a character is selected or in the world is game state, not a session state:
 * an input that needs it is a {@link ContextBound}.
 */
public enum SessionState {
    CONNECTED,
    AUTHED,
    CLOSING,
    DISCONNECTED;

    public boolean canMoveTo(SessionState next) {
        return switch (this) {
            case CONNECTED -> (next == AUTHED) || (next == CLOSING);
            case AUTHED -> next == CLOSING;
            case CLOSING -> next == DISCONNECTED;
            case DISCONNECTED -> false;
        };
    }
}
