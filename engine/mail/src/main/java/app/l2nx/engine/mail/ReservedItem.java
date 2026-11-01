/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import lombok.Builder;

/** A reserved item and the part of it that leaves: the whole row when {@code count} equals the row's count. */
@Builder(toBuilder = true)
public record ReservedItem(ItemRowState row, long count) {
    public ReservedItem {
        if ((count < 1) || (count > row.count())) {
            throw new IllegalArgumentException("Cannot move " + count + " of " + row);
        }
    }

    public boolean wholeRow() {
        return count == row.count();
    }
}
