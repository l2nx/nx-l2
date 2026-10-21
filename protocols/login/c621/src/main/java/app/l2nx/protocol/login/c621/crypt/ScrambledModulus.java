/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.protocol.c621.crypt;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * The 1024-bit RSA modulus as {@code Init} carries it: 128 bytes shuffled and XORed so that it is not plain in the
 * packet. The public exponent is always F4 (65537).
 */
public final class ScrambledModulus {
    public static final int SIZE = 0x80;

    private ScrambledModulus() {}

    public static byte[] scramble(BigInteger modulus) {
        byte[] scrambledMod = modulus.toByteArray();
        if ((scrambledMod.length == (SIZE + 1)) && (scrambledMod[0] == 0x00)) {
            final byte[] temp = new byte[SIZE];
            System.arraycopy(scrambledMod, 1, temp, 0, SIZE);
            scrambledMod = temp;
        }

        for (int i = 0; i < 4; i++) {
            final byte temp = scrambledMod[0x00 + i];
            scrambledMod[0x00 + i] = scrambledMod[0x4d + i];
            scrambledMod[0x4d + i] = temp;
        }

        for (int i = 0; i < 0x40; i++) {
            scrambledMod[i] = (byte) (scrambledMod[i] ^ scrambledMod[0x40 + i]);
        }

        for (int i = 0; i < 4; i++) {
            scrambledMod[0x0d + i] = (byte) (scrambledMod[0x0d + i] ^ scrambledMod[0x34 + i]);
        }

        for (int i = 0; i < 0x40; i++) {
            scrambledMod[0x40 + i] = (byte) (scrambledMod[0x40 + i] ^ scrambledMod[i]);
        }

        return scrambledMod;
    }

    public static BigInteger unscramble(byte[] scrambled) {
        if (scrambled.length != SIZE) {
            throw new IllegalArgumentException("A scrambled modulus has " + SIZE + " bytes, not " + scrambled.length);
        }
        final byte[] modulus = scrambled.clone();
        for (int i = 0; i < 0x40; i++) {
            modulus[0x40 + i] = (byte) (modulus[0x40 + i] ^ modulus[i]);
        }
        for (int i = 0; i < 4; i++) {
            modulus[0x0d + i] = (byte) (modulus[0x0d + i] ^ modulus[0x34 + i]);
        }
        for (int i = 0; i < 0x40; i++) {
            modulus[i] = (byte) (modulus[i] ^ modulus[0x40 + i]);
        }
        for (int i = 0; i < 4; i++) {
            final byte temp = modulus[i];
            modulus[i] = modulus[0x4d + i];
            modulus[0x4d + i] = temp;
        }
        return new BigInteger(1, modulus);
    }

    public static RSAPublicKey publicKey(byte[] scrambled) throws GeneralSecurityException {
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(unscramble(scrambled), RSAKeyGenParameterSpec.F4));
    }
}
