/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Objects;

/**
 * Checksum stored in the last 32-bit word of a region: the XOR of all preceding little-endian words.
 */
public final class XorChecksum {
    private static final int WORD_SIZE = 4;

    private XorChecksum() {}

    public static void append(byte[] data, int offset, int size) {
        append(Unpooled.wrappedBuffer(data), offset, size);
    }

    public static boolean verify(byte[] data, int offset, int size) {
        return verify(Unpooled.wrappedBuffer(data), offset, size);
    }

    public static void append(ByteBuf buf, int index, int size) {
        Objects.checkFromIndexSize(index, size, buf.capacity());
        if (((size % WORD_SIZE) != 0) || (size < WORD_SIZE)) {
            throw new IllegalArgumentException("Size " + size + " is not a positive multiple of " + WORD_SIZE);
        }

        final int checksumIndex = (index + size) - WORD_SIZE;
        buf.setIntLE(checksumIndex, xorWords(buf, index, checksumIndex));
    }

    public static boolean verify(ByteBuf buf, int index, int size) {
        Objects.checkFromIndexSize(index, size, buf.capacity());
        if (((size % WORD_SIZE) != 0) || (size <= WORD_SIZE)) {
            return false;
        }

        final int checksumIndex = (index + size) - WORD_SIZE;
        return xorWords(buf, index, checksumIndex) == buf.getIntLE(checksumIndex);
    }

    private static int xorWords(ByteBuf buf, int from, int to) {
        int checksum = 0;
        for (int i = from; i < to; i += WORD_SIZE) {
            checksum ^= buf.getIntLE(i);
        }
        return checksum;
    }
}
