/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import app.l2nx.engine.core.session.Session;

/**
 * Reads a {@link ContextCommandHandler}'s game context from the session just before the command runs. Stateless: one
 * instance per context.
 */
public interface ContextResolver<X> {
    /** Tag value of the rejection meter and log: keep to a small fixed set. */
    String name();

    /** @return {@code null} when the session lacks the context now; the command is then rejected, not run */
    X resolve(Session session);
}
