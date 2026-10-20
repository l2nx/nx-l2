/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

/** Data sits after {@link #PREFIX} bytes so that code ignoring absolute indexes fails. */
enum BufferKind {
    HEAP,
    DIRECT,
    COMPOSITE;

    static final int PREFIX = 3;

    ByteBuf wrap(byte[] data) {
        final byte[] prefixed = new byte[PREFIX + data.length];
        System.arraycopy(data, 0, prefixed, PREFIX, data.length);
        return switch (this) {
            case HEAP -> Unpooled.wrappedBuffer(prefixed);
            case DIRECT -> Unpooled.directBuffer(prefixed.length).writeBytes(prefixed);
            case COMPOSITE -> {
                final CompositeByteBuf composite = Unpooled.compositeBuffer();
                final int half = prefixed.length / 2;
                composite.addComponent(true, Unpooled.copiedBuffer(prefixed, 0, half));
                composite.addComponent(true, Unpooled.copiedBuffer(prefixed, half, prefixed.length - half));
                yield composite;
            }
        };
    }

    static int size(ByteBuf buf) {
        return buf.capacity() - PREFIX;
    }

    static byte[] region(ByteBuf buf) {
        return ByteBufUtil.getBytes(buf, PREFIX, size(buf));
    }
}
