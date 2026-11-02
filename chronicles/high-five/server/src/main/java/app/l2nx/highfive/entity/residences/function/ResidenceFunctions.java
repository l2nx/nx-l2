/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences.function;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.IntConsumer;

/**
 * The functions of one residence by type. A function that leaves, removed or replaced, has its pending fee cancelled.
 */
@SingleWriter
public final class ResidenceFunctions<F extends AbstractResidenceFunction> {
    private final Int2ObjectOpenHashMap<F> byType = new Int2ObjectOpenHashMap<>();

    /**
     * @return the function of the type, {@code null} for none
     */
    public F get(int type) {
        WorldPartition.checkOwner();
        return byType.get(type);
    }

    /** Adds the function, replacing the one of its type. */
    public void put(F function) {
        WorldPartition.checkOwner();
        final F replaced = byType.put(function.getType(), function);
        if (replaced != null) {
            replaced.cancelFee();
        }
    }

    public void remove(int type) {
        WorldPartition.checkOwner();
        final F removed = byType.remove(type);
        if (removed != null) {
            removed.cancelFee();
        }
    }

    /** Walks a copy of the types, so the action may remove functions. */
    public void forEachType(IntConsumer action) {
        WorldPartition.checkOwner();
        try (IntSnapshot types = ScratchBuffers.intSnapshot(byType.keySet())) {
            for (int i = 0; i < types.size(); i++) {
                action.accept(types.getInt(i));
            }
        }
    }
}
