/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.network.codec.Frames;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * The version-check answer with result 0 ("wrong protocol"), which the client shows as a version mismatch. No
 * adapter exists for an unknown version, so it lives here.
 */
public final class HandshakeRefusal {
    private static final int VERSION_CHECK = 0x2E;
    private static final int LENGTH = 23;

    private HandshakeRefusal() {}

    public static ByteBuf frame(ByteBufAllocator alloc) {
        return Frames.allocate(alloc, LENGTH)
                .writeByte(VERSION_CHECK)
                .writeByte(0)
                .writeZero(8)
                .writeIntLE(0)
                .writeIntLE(0)
                .writeByte(1)
                .writeIntLE(0);
    }
}
