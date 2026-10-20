/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.buffer.ByteBuf;

/**
 * Lineage 2 strings: UTF-16LE code units, terminated by a 0 unit or prefixed by a 2-byte count. Reads are bounded by
 * the frame and optionally by a character count.
 */
public final class Wire {
    /** The count is read back as a signed short. */
    public static final int MAX_SIZED_CHARS = 0x7FFF;

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

    public static String readSizedString(ByteBuf buf) {
        if (buf.readableBytes() < 2) {
            throw new PacketDecodeException("Sized string without its count");
        }
        final int count = buf.readShortLE();
        if ((count < 0) || ((count * 2) > buf.readableBytes())) {
            throw new PacketDecodeException("Sized string of " + count + " characters exceeds the frame");
        }
        final char[] text = new char[count];
        for (int i = 0; i < count; i++) {
            text[i] = (char) buf.readShortLE();
        }
        return new String(text);
    }

    public static void writeString(ByteBuf buf, CharSequence text) {
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                buf.writeShortLE(text.charAt(i));
            }
        }
        buf.writeShortLE(0);
    }

    public static void writeSizedString(ByteBuf buf, CharSequence text) {
        if ((text == null) || text.isEmpty()) {
            buf.writeShortLE(0);
            return;
        }
        if (text.length() > MAX_SIZED_CHARS) {
            throw new IllegalArgumentException(
                    "Sized string of " + text.length() + " characters exceeds " + MAX_SIZED_CHARS);
        }
        buf.writeShortLE(text.length());
        for (int i = 0; i < text.length(); i++) {
            buf.writeShortLE(text.charAt(i));
        }
    }
}
