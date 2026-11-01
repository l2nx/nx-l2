/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.List;

/**
 * What a caller of {@link ValueTransactionSubmitter} does with each definite outcome; exactly one method is called, on
 * the loop the transaction was submitted from.
 */
public interface ValueOutcomeHandler<R> {
    void committed(R result);

    /**
     * The transaction committed, but its result is not known here: a replay of the key, or an unknown outcome the
     * lookup found committed.
     */
    void replayed(List<ItemMovement> movements);

    /**
     * A condition failed inside the transaction; nothing changed.
     *
     * @param reason the consumer's own enum, as its transaction threw it in {@link ValueRefusal}
     * @param param {@code null} unless the reason names a value
     */
    void refused(Enum<?> reason, String param);

    /** The transaction did not commit and now cannot; nothing changed. */
    void failed();
}
