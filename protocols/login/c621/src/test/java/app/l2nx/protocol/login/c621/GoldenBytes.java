/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GoldenBytes {
    private static final HexFormat HEX = HexFormat.of();

    private GoldenBytes() {}

    public static String written(Consumer<ByteBuf> writer) {
        final ByteBuf out = Unpooled.buffer();
        try {
            writer.accept(out);
            return HEX.formatHex(ByteBufUtil.getBytes(out));
        } finally {
            out.release();
        }
    }

    /** Reads the body that follows the opcode; the buffer is released whatever the reader does. */
    public static <T> T read(String body, Function<ByteBuf, T> reader) {
        final ByteBuf in = Unpooled.wrappedBuffer(HEX.parseHex(body));
        try {
            return reader.apply(in);
        } finally {
            in.release();
        }
    }

    public static byte[] bytes(String hex) {
        return HEX.parseHex(hex);
    }

    public static byte[] pattern(int length, int seed) {
        final byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((i * 7) + seed);
        }
        return bytes;
    }

    public static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }
}
