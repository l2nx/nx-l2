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
 * Snapshot lists for call-out loops, one per nesting depth since a callback may re-enter. Lease in a
 * try-with-resources so leases close innermost first.
 */
@SingleWriter
public final class ScratchBuffers {
    private static final ObjectArrayList<Snapshot<Object>> BY_DEPTH = new ObjectArrayList<>();
    private static int depth;

    private ScratchBuffers() {}

    public static <T> Snapshot<T> snapshot(Collection<? extends T> source) {
        WorldPartition.checkOwner();
        final Snapshot<T> buffer = lease();
        try {
            buffer.fill(source);
        } catch (RuntimeException | Error e) {
            // The caller never receives the lease, so it cannot close it.
            release(buffer);
            throw e;
        }

        return buffer;
    }

    public static <T> Snapshot<T> empty() {
        WorldPartition.checkOwner();
        return lease();
    }

    @SuppressWarnings("unchecked")
    private static <T> Snapshot<T> lease() {
        if (depth == BY_DEPTH.size()) {
            BY_DEPTH.add(new Snapshot<>());
        }

        return (Snapshot<T>) (Snapshot<?>) BY_DEPTH.get(depth++);
    }

    private static void release(Snapshot<?> buffer) {
        if ((depth == 0) || (buffer != BY_DEPTH.get(depth - 1))) {
            throw new IllegalStateException("Scratch buffers released out of order");
        }

        buffer.clear();
        depth--;
    }

    @SingleWriter
    public static final class Snapshot<T> extends ObjectArrayList<T> implements AutoCloseable {
        private Snapshot() {}

        public Snapshot<T> refill(Collection<? extends T> source) {
            WorldPartition.checkOwner();
            fill(source);
            return this;
        }

        private void fill(Collection<? extends T> source) {
            size(source.size());
            // The backing array is Object[], which a generic T[] erases to, so the array-backed toArray copies into it.
            source.toArray(elements());
        }

        @Override
        public void close() {
            WorldPartition.checkOwner();
            release(this);
        }
    }
}
