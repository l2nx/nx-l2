/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

/**
 * A client command's handler that takes a game context. It runs in the drain of the command's own session; a
 * session without the context rejects the command. Server-addressed commands carry no context, so never have one.
 */
public non-sealed interface ContextCommandHandler<X, C extends Command> extends CommandHandler<C> {
    ContextResolver<X> context();

    void handle(X context, C command);
}
