/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Serialized on the thread that sends it, the owner of the state it reads; the IO thread only encrypts.
 */
public abstract class OutgoingPacket<C> {
    private static final int DEFAULT_SIZE_HINT = 64;

    /** A frame with header room the caller owns, or {@code null} when nothing is to be sent. */
    public ByteBuf serialize(C client, ByteBufAllocator alloc) {
        final ByteBuf frame = Frames.allocate(alloc, sizeHint() + tailroom());
        final boolean written;
        try {
            written = write(client, new PacketWriter(frame));
        } catch (RuntimeException e) {
            frame.release();
            throw e;
        }
        if (!written) {
            frame.release();
            return null;
        }
        return frame;
    }

    protected int sizeHint() {
        return DEFAULT_SIZE_HINT;
    }

    protected int tailroom() {
        return 0;
    }

    protected abstract boolean write(C client, PacketWriter writer);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
