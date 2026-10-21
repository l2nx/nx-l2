/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import app.l2nx.engine.core.session.Session;
import java.util.Optional;

@FunctionalInterface
public interface SessionDirectory {
    Optional<Session> find(SessionAddress address);
}
