/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

/** Runs on the serial lane of the service that owns the state, the thread that submits the command; no session. */
public non-sealed interface ServiceCommandHandler<C extends Command> extends CommandHandler<C> {
    void handle(C command);
}
