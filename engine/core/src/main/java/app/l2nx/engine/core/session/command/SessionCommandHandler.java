/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import app.l2nx.engine.core.session.Session;

public non-sealed interface SessionCommandHandler<C extends Command> extends CommandHandler<C> {
    void handle(Session session, C command);

    /**
     * Runs on the submitting thread when the address names no live session, so a push such as a kick can still
     * free the account's login-side state. Does nothing by default.
     */
    default void handleWithoutSession(C command) {}
}
