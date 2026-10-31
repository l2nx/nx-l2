/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.network.codec.Frames;
import app.l2nx.protocol.game.p267.serverpackets.KeyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public final class P267HandshakeRefusal {
    private static final KeyPacket REFUSAL = KeyPacket.builder()
            .accepted(false)
            .key(new byte[KeyPacket.KEY_BYTES])
            .serverId(0)
            .build();
    private static final int LENGTH = 23;

    private P267HandshakeRefusal() {}

    public static ByteBuf frame(ByteBufAllocator alloc) {
        final ByteBuf frame = Frames.allocate(alloc, LENGTH);
        REFUSAL.write(frame);
        return frame;
    }
}
