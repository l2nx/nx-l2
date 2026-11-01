/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** How a submitted value transaction ended, delivered on the loop that submitted it. */
public sealed interface ValueOutcome<R> {
    @Builder(toBuilder = true)
    record Committed<R>(R result, UUID transactionId) implements ValueOutcome<R> {}

    /**
     * The key was committed before: nothing was executed again.
     */
    @Builder(toBuilder = true)
    record Replayed<R>(UUID transactionId, UUID subjectId, List<ItemMovement> movements) implements ValueOutcome<R> {
        public Replayed {
            movements = List.copyOf(movements);
        }
    }

    /** A condition failed inside the transaction; nothing changed. */
    @Builder(toBuilder = true)
    record Refused<R>(Enum<?> reason, String param) implements ValueOutcome<R> {}

    /**
     * The transaction failed before its commit (an error in {@link ValueTransaction#execute}, a lost connection, a
     * full executor); nothing changed, so the caller releases its reservations.
     */
    record Failed<R>(Throwable cause) implements ValueOutcome<R> {}

    /**
     * The commit outcome is not known (connection lost around the commit). The caller keeps its reservations and asks
     * {@link ValueTransactions#lookup} by the key.
     */
    record Unknown<R>(Throwable cause) implements ValueOutcome<R> {}
}
