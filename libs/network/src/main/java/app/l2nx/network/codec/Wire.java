/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.buffer.ByteBuf;

/** Lineage 2 strings: UTF-16LE code units terminated by a 0 unit. */
public final class Wire {
    private Wire() {}

    public static String readString(ByteBuf buf) {
        return readString(buf, Integer.MAX_VALUE);
    }

    public static String readString(ByteBuf buf, int maxChars) {
        final StringBuilder text = new StringBuilder();
        while (buf.readableBytes() >= 2) {
            final char unit = (char) buf.readShortLE();
            if (unit == 0) {
                return text.toString();
            }
            if (text.length() == maxChars) {
                throw new PacketDecodeException("String longer than " + maxChars + " characters");
            }
            text.append(unit);
        }
        throw new PacketDecodeException("String without terminator");
    }

    /** Lenient: a frame that ends before the terminator yields the characters read so far. */
    public static String readStringToEnd(ByteBuf buf) {
        final StringBuilder text = new StringBuilder();
        while (buf.readableBytes() >= 2) {
            final char unit = (char) buf.readShortLE();
            if (unit == 0) {
                break;
            }
            text.append(unit);
        }
        return text.toString();
    }

    public static void writeString(ByteBuf buf, CharSequence text) {
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                buf.writeShortLE(text.charAt(i));
            }
        }
        buf.writeShortLE(0);
    }
}
