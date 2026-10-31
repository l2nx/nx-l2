/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;

/**
 * Snapshot lists for call-out loops, one per nesting depth since a callback may re-enter. Pair every
 * {@link #acquire()} with a {@link #release} in a {@code finally}, innermost first.
 */
@SingleWriter
public final class ScratchBuffers {
    private static final ObjectArrayList<ObjectArrayList<Object>> BY_DEPTH = new ObjectArrayList<>();
    private static int depth;

    private ScratchBuffers() {}

    @SuppressWarnings("unchecked")
    public static <T> ObjectArrayList<T> acquire() {
        WorldPartition.checkOwner();
        if (depth == BY_DEPTH.size()) {
            BY_DEPTH.add(new ObjectArrayList<>());
        }

        return (ObjectArrayList<T>) (ObjectArrayList<?>) BY_DEPTH.get(depth++);
    }

    public static <T> void copy(Collection<? extends T> source, ObjectArrayList<T> buffer) {
        WorldPartition.checkOwner();
        buffer.size(source.size());
        // The backing array is Object[], which a generic T[] erases to, so the array-backed toArray copies into it.
        source.toArray(buffer.elements());
    }

    public static void release(ObjectArrayList<?> buffer) {
        WorldPartition.checkOwner();
        if ((depth == 0) || (buffer != BY_DEPTH.get(depth - 1))) {
            throw new IllegalStateException("Scratch buffers released out of order");
        }

        buffer.clear();
        depth--;
    }
}
