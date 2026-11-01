/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Item handles reserved by value transactions in flight, used only on the owning partition's loop: a pending item
 * cannot be used, moved or saved until the transaction's outcome releases it.
 */
public final class PendingItems {
    private final IntSet pending = new IntOpenHashSet();

    /** Reserves all handles or none: {@code false} when one of them is already pending. */
    public boolean reserve(IntCollection handles) {
        for (int handle : handles) {
            if (pending.contains(handle)) {
                return false;
            }
        }
        pending.addAll(handles);
        return true;
    }

    public void release(IntCollection handles) {
        pending.removeAll(handles);
    }

    public boolean isPending(int handle) {
        return pending.contains(handle);
    }
}
