/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import java.util.UUID;

public sealed interface SessionAddress {
    record Account(UUID accountId) implements SessionAddress {}

    record Request(UUID requestId) implements SessionAddress {}
}
