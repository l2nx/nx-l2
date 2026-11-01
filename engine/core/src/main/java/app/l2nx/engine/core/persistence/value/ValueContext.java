/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public interface ValueContext {
    /** Bound to the open transaction. */
    JdbcClient jdbc();

    UUID transactionId();

    /** Wall-clock instant of the transaction, the same for every write in it. */
    Instant now();

    void record(ItemMovement movement);

    default void recordAll(Collection<ItemMovement> movements) {
        movements.forEach(this::record);
    }
}
