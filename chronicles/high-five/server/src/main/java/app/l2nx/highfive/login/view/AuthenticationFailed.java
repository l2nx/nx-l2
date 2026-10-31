/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;

/** The game server cannot admit the client now; the client shows a login failure and returns to the login screen. */
public record AuthenticationFailed() implements ViewEvent {
    public static final AuthenticationFailed INSTANCE = new AuthenticationFailed();
}
