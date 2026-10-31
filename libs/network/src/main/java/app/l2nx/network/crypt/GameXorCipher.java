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
import io.netty.buffer.Unpooled;

/**
 * The rolling XOR of the game protocol. Each direction has its own 16-byte key whose bytes 8..11 are a little-endian
 * counter advanced by every frame's length, so both sides must see every frame, in order.
 */
public final class GameXorCipher {
    private static final int KEY_LENGTH = 16;
    private static final int KEY_INDEX_MASK = KEY_LENGTH - 1;
    private static final int COUNTER_INDEX = 8;

    private final byte[] inKey = new byte[KEY_LENGTH];
    private final byte[] outKey = new byte[KEY_LENGTH];
    private final ByteBuf inCounter = Unpooled.wrappedBuffer(inKey);
    private final ByteBuf outCounter = Unpooled.wrappedBuffer(outKey);

    public void setKey(byte[] key) {
        if ((key == null) || (key.length < KEY_LENGTH)) {
            throw new IllegalArgumentException("The game XOR key has 16 bytes");
        }
        System.arraycopy(key, 0, inKey, 0, KEY_LENGTH);
        System.arraycopy(key, 0, outKey, 0, KEY_LENGTH);
    }

    public void encrypt(ByteBuf buf, int index, int size) {
        int previous = 0;
        for (int i = 0; i < size; i++) {
            previous = buf.getByte(index + i) ^ outKey[i & KEY_INDEX_MASK] ^ previous;
            buf.setByte(index + i, previous);
        }
        advance(outCounter, size);
    }

    public void decrypt(ByteBuf buf, int index, int size) {
        int previous = 0;
        for (int i = 0; i < size; i++) {
            final int encrypted = buf.getByte(index + i);
            buf.setByte(index + i, encrypted ^ inKey[i & KEY_INDEX_MASK] ^ previous);
            previous = encrypted;
        }
        advance(inCounter, size);
    }

    private static void advance(ByteBuf key, int size) {
        key.setIntLE(COUNTER_INDEX, key.getIntLE(COUNTER_INDEX) + size);
    }
}
