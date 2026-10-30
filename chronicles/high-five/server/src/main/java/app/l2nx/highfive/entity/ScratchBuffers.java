/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import app.l2nx.engine.core.loop.SingleWriter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;

/**
 * Reusable snapshot lists for the call-out loops of {@link World} and {@link WorldRegion}. A callback may re-enter
 * and snapshot again, hence one list per nesting depth; every {@link #acquire()} is paired with a {@link #release} in
 * a {@code finally}, innermost first.
 */
@SingleWriter
final class ScratchBuffers {
    private static final ObjectArrayList<ObjectArrayList<Object>> BY_DEPTH = new ObjectArrayList<>();
    private static int depth;

    private ScratchBuffers() {}

    @SuppressWarnings("unchecked")
    static <T> ObjectArrayList<T> acquire() {
        if (depth == BY_DEPTH.size()) {
            BY_DEPTH.add(new ObjectArrayList<>());
        }

        return (ObjectArrayList<T>) (ObjectArrayList<?>) BY_DEPTH.get(depth++);
    }

    static <T> void copy(Collection<? extends T> source, ObjectArrayList<T> buffer) {
        buffer.size(source.size());
        // The backing array is Object[], which a generic T[] erases to, so the array-backed toArray copies into it.
        source.toArray(buffer.elements());
    }

    static void release(ObjectArrayList<?> buffer) {
        if ((depth == 0) || (buffer != BY_DEPTH.get(depth - 1))) {
            throw new IllegalStateException("Scratch buffers released out of order");
        }

        buffer.clear();
        depth--;
    }
}
