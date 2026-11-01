/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Stackable templates an in-flight transaction stores as a new row. A stack of one the character gets meanwhile stays
 * unsaved and merges into that row on apply, so memory and the database keep one stack. Loop-confined.
 */
public final class AwaitedStacks {
    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    public void await(int ownerHandle, IntCollection itemTemplateIds) {
        for (int itemTemplateId : itemTemplateIds) {
            counts.addTo(key(ownerHandle, itemTemplateId), 1);
        }
    }

    public void release(int ownerHandle, IntCollection itemTemplateIds) {
        for (int itemTemplateId : itemTemplateIds) {
            final long key = key(ownerHandle, itemTemplateId);
            final int count = counts.get(key);
            if (count <= 1) {
                counts.remove(key);
            } else {
                counts.put(key, count - 1);
            }
        }
    }

    public boolean isAwaited(int ownerHandle, int itemTemplateId) {
        return counts.containsKey(key(ownerHandle, itemTemplateId));
    }

    private static long key(int ownerHandle, int itemTemplateId) {
        return ((long) ownerHandle << Integer.SIZE) | (itemTemplateId & 0xFFFF_FFFFL);
    }
}
