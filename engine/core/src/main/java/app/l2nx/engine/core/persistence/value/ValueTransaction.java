/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.UUID;

/**
 * One value move, executed in one database transaction off the loop. Everything the partition knows (reserved rows,
 * absolute counts of online characters, snapshots) is captured in the instance before it is submitted; {@link
 * #execute} must not touch game objects.
 */
public interface ValueTransaction<R> {
    ValueTransactionType type();

    UUID idempotencyKey();

    R execute(ValueContext context) throws ValueRefusal;

    /** The main entity of a committed result (a mail), recorded so a replay can name it; {@code null} when none. */
    default UUID subjectId(R result) {
        return null;
    }
}
