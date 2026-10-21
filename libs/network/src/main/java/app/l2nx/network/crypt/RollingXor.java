/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Objects;

/**
 * XOR over little-endian 32-bit words with a rolling key that grows by each plain word. The first and last words stay
 * plain; the final key replaces the second-to-last word, which is how the decrypting side recovers it.
 */
public final class RollingXor {
    private static final int WORD_SIZE = 4;
    private static final int MIN_SIZE = 3 * WORD_SIZE;

    private RollingXor() {}

    public static void encrypt(byte[] data, int offset, int size, int key) {
        encrypt(Unpooled.wrappedBuffer(data), offset, size, key);
    }

    public static void encrypt(ByteBuf buf, int index, int size, int key) {
        Objects.checkFromIndexSize(index, size, buf.capacity());
        checkSize(size);

        final int keyIndex = (index + size) - (2 * WORD_SIZE);
        int rollingKey = key;
        for (int i = index + WORD_SIZE; i < keyIndex; i += WORD_SIZE) {
            final int word = buf.getIntLE(i);
            rollingKey += word;
            buf.setIntLE(i, word ^ rollingKey);
        }
        buf.setIntLE(keyIndex, rollingKey);
    }

    public static void decrypt(byte[] data, int offset, int size) {
        decrypt(Unpooled.wrappedBuffer(data), offset, size);
    }

    /** Walks back from the final key; the word that carried it keeps it, since the original word is lost. */
    public static void decrypt(ByteBuf buf, int index, int size) {
        Objects.checkFromIndexSize(index, size, buf.capacity());
        checkSize(size);

        final int keyIndex = (index + size) - (2 * WORD_SIZE);
        int rollingKey = buf.getIntLE(keyIndex);
        for (int i = keyIndex - WORD_SIZE; i > index; i -= WORD_SIZE) {
            final int word = buf.getIntLE(i) ^ rollingKey;
            buf.setIntLE(i, word);
            rollingKey -= word;
        }
    }

    private static void checkSize(int size) {
        if (((size % WORD_SIZE) != 0) || (size < MIN_SIZE)) {
            throw new IllegalArgumentException(
                    "Size " + size + " is not a multiple of " + WORD_SIZE + " of at least " + MIN_SIZE);
        }
    }
}
