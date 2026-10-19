/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * XOR over little-endian 32-bit words with a rolling key: before each word is XORed, the key grows by the plain word.<br>
 * Region layout: the first word is left as is, the words up to the last two are encrypted, the final key is written to the second-to-last word and the last word is left as is.
 */
public final class RollingXor {
    private static final int WORD_SIZE = 4;
    private static final int MIN_SIZE = 3 * WORD_SIZE;
    private static final VarHandle WORD = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private RollingXor() {}

    /**
     * Encrypts the region in place and stores the final key in it.
     * @throws IllegalArgumentException if {@code size} is not a multiple of 4 or is below 12
     */
    public static void encrypt(byte[] data, int offset, int size, int key) {
        Objects.checkFromIndexSize(offset, size, data.length);
        if (((size % WORD_SIZE) != 0) || (size < MIN_SIZE)) {
            throw new IllegalArgumentException(
                    "Size " + size + " is not a multiple of " + WORD_SIZE + " of at least " + MIN_SIZE);
        }

        final int keyOffset = (offset + size) - (2 * WORD_SIZE);
        int rollingKey = key;
        for (int i = offset + WORD_SIZE; i < keyOffset; i += WORD_SIZE) {
            final int word = (int) WORD.get(data, i);
            rollingKey += word;
            WORD.set(data, i, word ^ rollingKey);
        }
        WORD.set(data, keyOffset, rollingKey);
    }
}
