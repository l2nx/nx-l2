/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.network.crypt;

import io.netty.buffer.ByteBuf;

/**
 * Stateful XOR cipher of the game protocol: a 16-byte session key per direction whose bytes 8..11 hold a rolling
 * offset advanced by every frame.
 */
public class GameXorCipher {
    private static final int KEY_LENGTH = 16;
    private static final int BYTE_MASK = 0xFF;
    private static final int NIBBLE_MASK = 0x0F;
    private static final int SHIFT_8 = 8;
    private static final int SHIFT_16 = 16;
    private static final int SHIFT_24 = 24;

    // Rolling offset is stored at key[8..11] (little-endian).
    private static final int OFFSET_INDEX = 8;

    private final byte[] inKey = new byte[KEY_LENGTH];
    private final byte[] outKey = new byte[KEY_LENGTH];

    // Pre-zero-extended mirrors of inKey / outKey for the hot XOR loops.
    private final int[] inKeyMasked = new int[KEY_LENGTH];
    private final int[] outKeyMasked = new int[KEY_LENGTH];

    public void setKey(byte[] key) {
        if ((key == null) || (key.length < KEY_LENGTH)) {
            throw new IllegalArgumentException("GameXorCipher key must be at least 16 bytes.");
        }

        System.arraycopy(key, 0, inKey, 0, KEY_LENGTH);
        System.arraycopy(key, 0, outKey, 0, KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            final int v = key[i] & BYTE_MASK;
            inKeyMasked[i] = v;
            outKeyMasked[i] = v;
        }
    }

    public void encrypt(ByteBuf buf, int index, int size) {
        final int[] key = outKeyMasked;
        int prev = 0;
        for (int i = 0; i < size; i++) {
            prev = buf.getUnsignedByte(index + i) ^ key[i & NIBBLE_MASK] ^ prev;
            buf.setByte(index + i, prev);
        }
        advanceOffset(outKey, outKeyMasked, size);
    }

    public void decrypt(ByteBuf buf, int index, int size) {
        final int[] key = inKeyMasked;
        int last = 0;
        for (int i = 0; i < size; i++) {
            final int enc = buf.getUnsignedByte(index + i);
            buf.setByte(index + i, enc ^ key[i & NIBBLE_MASK] ^ last);
            last = enc;
        }
        advanceOffset(inKey, inKeyMasked, size);
    }

    private static void advanceOffset(byte[] key, int[] keyMasked, int size) {
        int old = (key[OFFSET_INDEX] & BYTE_MASK);
        old |= (key[OFFSET_INDEX + 1] & BYTE_MASK) << SHIFT_8;
        old |= (key[OFFSET_INDEX + 2] & BYTE_MASK) << SHIFT_16;
        old |= (key[OFFSET_INDEX + 3] & BYTE_MASK) << SHIFT_24;

        old += size;

        final byte b0 = (byte) (old & BYTE_MASK);
        final byte b1 = (byte) ((old >> SHIFT_8) & BYTE_MASK);
        final byte b2 = (byte) ((old >> SHIFT_16) & BYTE_MASK);
        final byte b3 = (byte) ((old >> SHIFT_24) & BYTE_MASK);

        key[OFFSET_INDEX] = b0;
        key[OFFSET_INDEX + 1] = b1;
        key[OFFSET_INDEX + 2] = b2;
        key[OFFSET_INDEX + 3] = b3;

        keyMasked[OFFSET_INDEX] = b0 & BYTE_MASK;
        keyMasked[OFFSET_INDEX + 1] = b1 & BYTE_MASK;
        keyMasked[OFFSET_INDEX + 2] = b2 & BYTE_MASK;
        keyMasked[OFFSET_INDEX + 3] = b3 & BYTE_MASK;
    }
}
