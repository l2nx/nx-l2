/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.nio.ByteOrder;

/**
 * Lineage 2 frame: a little-endian 2-byte length that counts itself, then the payload.
 */
public final class Frames {
    public static final int HEADER = 2;
    public static final int MAX_LENGTH = 0xFFFF;

    private Frames() {}

    /** Hands out retained slices of the cumulation, which the cipher decrypts in place. */
    public static LengthFieldBasedFrameDecoder decoder(int maxFrame) {
        return new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, maxFrame, 0, HEADER, -HEADER, HEADER, true);
    }

    public static ByteBuf allocate(ByteBufAllocator alloc, int payloadHint) {
        return alloc.buffer(HEADER + payloadHint).writeZero(HEADER);
    }

    /**
     * A plain duplicate shares the reference count of its root, so every holder sees the others; a pooled
     * {@code retainedDuplicate()} keeps its own count and hides them.
     */
    public static ByteBuf share(ByteBuf frame) {
        return frame.duplicate().retain();
    }

    /**
     * True while another holder can still read this content; such a frame must not be encrypted in place. The whole
     * unwrap chain is checked: a leak-aware wrapper adds a level between a pooled derived buffer and its root.
     */
    public static boolean isShared(ByteBuf frame) {
        for (ByteBuf level = frame; level != null; level = level.unwrap()) {
            if (level.refCnt() > 1) {
                return true;
            }
        }
        return false;
    }
}
