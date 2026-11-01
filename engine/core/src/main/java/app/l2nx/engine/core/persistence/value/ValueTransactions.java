/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Runs each value transaction off the loop; the outcome is offered to {@code resultLoop}. */
public interface ValueTransactions {
    <R> void submit(ValueTransaction<R> transaction, Executor resultLoop, Consumer<ValueOutcome<R>> onOutcome);

    /**
     * Resolves an {@link ValueOutcome.Unknown}: {@link ValueOutcome.Replayed} when the key was committed, {@link
     * ValueOutcome.Failed} when it was not and now cannot be, {@link ValueOutcome.Unknown} again when the database gave
     * no answer.
     */
    void lookup(
            ValueTransactionType type,
            UUID idempotencyKey,
            Executor resultLoop,
            Consumer<ValueOutcome<Void>> onOutcome);
}
