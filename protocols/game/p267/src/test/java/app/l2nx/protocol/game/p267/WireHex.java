/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.function.Consumer;

public final class WireHex {
    private WireHex() {}

    public static String c(int value) {
        return hex(out -> out.writeByte(value));
    }

    public static String h(int value) {
        return hex(out -> out.writeShortLE(value));
    }

    public static String d(int value) {
        return hex(out -> out.writeIntLE(value));
    }

    public static String q(long value) {
        return hex(out -> out.writeLongLE(value));
    }

    public static String f(double value) {
        return hex(out -> out.writeDoubleLE(value));
    }

    public static String s(String text) {
        return hex(out -> {
            for (int i = 0; i < text.length(); i++) {
                out.writeShortLE(text.charAt(i));
            }
            out.writeShortLE(0);
        });
    }

    public static String zeros(int bytes) {
        return "00".repeat(bytes);
    }

    private static String hex(Consumer<ByteBuf> writer) {
        final ByteBuf out = Unpooled.buffer();
        try {
            writer.accept(out);
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(out));
        } finally {
            out.release();
        }
    }
}
