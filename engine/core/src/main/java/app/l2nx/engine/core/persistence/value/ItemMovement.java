/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/** One side of a value move, written as one ledger row: a positive delta arrives at the holder, a negative one leaves it. */
@Builder(toBuilder = true)
public record ItemMovement(HolderType holderType, UUID holderId, int itemTemplateId, UUID itemId, long delta) {
    public ItemMovement {
        Objects.requireNonNull(holderType, "holderType");
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(itemId, "itemId");
        if (delta == 0) {
            throw new IllegalArgumentException("A movement of zero is not a movement");
        }
    }
}
