/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Snapshot lists for call-out loops, one per nesting depth since a callback may re-enter. Lease in a
 * try-with-resources so leases close innermost first. Object and int snapshots have depths of their own, each closed
 * innermost first.
 */
@SingleWriter
public final class ScratchBuffers {
    private static final Depths<Snapshot<?>> SNAPSHOTS = new Depths<>(Snapshot::new);
    private static final Depths<IntSnapshot> INT_SNAPSHOTS = new Depths<>(IntSnapshot::new);

    private ScratchBuffers() {}

    public static <T> Snapshot<T> snapshot(Collection<? extends T> source) {
        WorldPartition.checkOwner();
        final Snapshot<T> buffer = lease();
        try {
            buffer.fill(source);
        } catch (RuntimeException | Error e) {
            // The caller never receives the lease, so it cannot close it.
            SNAPSHOTS.release(buffer);
            buffer.clear();
            throw e;
        }

        return buffer;
    }

    public static <T> Snapshot<T> empty() {
        WorldPartition.checkOwner();
        return lease();
    }

    public static IntSnapshot intEmpty() {
        WorldPartition.checkOwner();
        return INT_SNAPSHOTS.lease();
    }

    public static IntSnapshot intSnapshot(IntCollection source) {
        WorldPartition.checkOwner();
        final IntSnapshot buffer = INT_SNAPSHOTS.lease();
        try {
            buffer.fill(source);
        } catch (RuntimeException | Error e) {
            INT_SNAPSHOTS.release(buffer);
            buffer.clear();
            throw e;
        }

        return buffer;
    }

    @SuppressWarnings("unchecked")
    private static <T> Snapshot<T> lease() {
        return (Snapshot<T>) SNAPSHOTS.lease();
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
            SNAPSHOTS.release(this);
            clear();
        }
    }

    @SingleWriter
    public static final class IntSnapshot extends IntArrayList implements AutoCloseable {
        private IntSnapshot() {}

        private void fill(IntCollection source) {
            size(source.size());
            // Big enough after size(), so the collection copies into the backing array instead of allocating one.
            source.toArray(elements());
        }

        @Override
        public void close() {
            WorldPartition.checkOwner();
            INT_SNAPSHOTS.release(this);
            clear();
        }
    }

    private static final class Depths<B> {
        private final ObjectArrayList<B> byDepth = new ObjectArrayList<>();
        private final Supplier<B> factory;
        private int depth;

        private Depths(Supplier<B> factory) {
            this.factory = factory;
        }

        private B lease() {
            if (depth == byDepth.size()) {
                byDepth.add(factory.get());
            }

            return byDepth.get(depth++);
        }

        private void release(B buffer) {
            if ((depth == 0) || (buffer != byDepth.get(depth - 1))) {
                throw new IllegalStateException("Scratch buffers released out of order");
            }

            depth--;
        }
    }
}
