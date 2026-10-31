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
package app.l2nx.protocol.game.p267.crypt;

import java.security.SecureRandom;

public final class GameXorKeys {
    private static final int KEY_LENGTH_BYTES = 16;
    private static final int RANDOM_PREFIX_LENGTH = 8;

    private static final byte[] KEY_TAIL_BYTES = {
        (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97
    };

    private static final SecureRandom KEY_RANDOM = new SecureRandom();

    private GameXorKeys() {}

    public static byte[] getRandomKey() {
        final byte[] sent = new byte[RANDOM_PREFIX_LENGTH];
        KEY_RANDOM.nextBytes(sent);
        return fromSent(sent);
    }

    public static byte[] fromSent(byte[] sent) {
        if ((sent == null) || (sent.length != RANDOM_PREFIX_LENGTH)) {
            throw new IllegalArgumentException("The key packet carries " + RANDOM_PREFIX_LENGTH + " key bytes");
        }
        final byte[] key = new byte[KEY_LENGTH_BYTES];
        System.arraycopy(sent, 0, key, 0, RANDOM_PREFIX_LENGTH);
        System.arraycopy(KEY_TAIL_BYTES, 0, key, RANDOM_PREFIX_LENGTH, KEY_TAIL_BYTES.length);
        return key;
    }
}
