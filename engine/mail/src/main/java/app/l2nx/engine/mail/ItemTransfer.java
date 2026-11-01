/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.value.ItemMovement;
import java.util.List;
import lombok.Builder;

/** @param rows the rows as committed, put into an online character memory after the commit */
@Builder(toBuilder = true)
public record ItemTransfer(List<ItemMovement> movements, List<CommittedItemRow> rows) {
    public static final ItemTransfer NONE = new ItemTransfer(List.of(), List.of());

    public ItemTransfer {
        movements = List.copyOf(movements);
        rows = List.copyOf(rows);
    }
}
