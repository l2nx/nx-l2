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
package app.l2nx.protocol.login.c621.crypt;

import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import lombok.Builder;

/**
 * The login and password of {@link RequestAuthLogin}: UTF-8, zero-padded at fixed offsets of the decrypted blocks,
 * read up to the first zero byte and trimmed.
 *
 * @param user {@code null} when the field is blank or not valid UTF-8
 * @param password {@code null} when the field is blank or not valid UTF-8
 */
@Builder(toBuilder = true)
public record Credentials(String user, String password) {
    /** Raw RSA, as the client encrypts the blocks. */
    public static final String TRANSFORMATION = "RSA/ECB/NoPadding";

    private static final int BLOCK = RequestAuthLogin.BLOCK;

    private static final int ONE_BLOCK_USER = 0x5E;
    private static final int ONE_BLOCK_USER_SIZE = 14;
    private static final int ONE_BLOCK_PASSWORD = 0x6C;

    // Offsets into the two decrypted blocks back to back; the login straddles them.
    private static final int TWO_BLOCKS_USER = 0x4E;
    private static final int TWO_BLOCKS_USER_SIZE = 50;
    private static final int TWO_BLOCKS_USER_TAIL = 0xCE;
    private static final int TWO_BLOCKS_USER_TAIL_SIZE = 14;
    private static final int TWO_BLOCKS_PASSWORD = 0xDC;

    private static final int PASSWORD_SIZE = 16;

    public enum Layout {
        ONE_BLOCK,
        TWO_BLOCKS
    }

    public Credentials {
        if ((user != null) && user.isBlank()) {
            throw new IllegalArgumentException("A blank user is null");
        }
        if ((password != null) && password.isBlank()) {
            throw new IllegalArgumentException("A blank password is null");
        }
    }

    /**
     * @throws GeneralSecurityException {@link IllegalBlockSizeException} or {@link BadPaddingException} when a block
     *     is larger than the modulus; any other kind when RSA is unavailable or the key is unusable
     */
    public static Credentials decrypt(PrivateKey key, RequestAuthLogin packet) throws GeneralSecurityException {
        final Cipher rsa = Cipher.getInstance(TRANSFORMATION);
        rsa.init(Cipher.DECRYPT_MODE, key);
        final byte[] block2 = packet.block2();
        final byte[] decrypted = new byte[block2 != null ? 2 * BLOCK : BLOCK];
        rsa.doFinal(packet.block1(), 0, BLOCK, decrypted, 0);
        if (block2 != null) {
            rsa.doFinal(block2, 0, BLOCK, decrypted, BLOCK);
            // The two halves form one field: a zero ends the login wherever it falls.
            final byte[] user = new byte[TWO_BLOCKS_USER_SIZE + TWO_BLOCKS_USER_TAIL_SIZE];
            System.arraycopy(decrypted, TWO_BLOCKS_USER, user, 0, TWO_BLOCKS_USER_SIZE);
            System.arraycopy(decrypted, TWO_BLOCKS_USER_TAIL, user, TWO_BLOCKS_USER_SIZE, TWO_BLOCKS_USER_TAIL_SIZE);
            return Credentials.builder()
                    .user(field(user, 0, user.length))
                    .password(field(decrypted, TWO_BLOCKS_PASSWORD, PASSWORD_SIZE))
                    .build();
        }
        return Credentials.builder()
                .user(field(decrypted, ONE_BLOCK_USER, ONE_BLOCK_USER_SIZE))
                .password(field(decrypted, ONE_BLOCK_PASSWORD, PASSWORD_SIZE))
                .build();
    }

    /**
     * A {@code null} user or password leaves its field empty.
     *
     * @throws IllegalArgumentException when the user or password does not fit the layout in UTF-8, holds a zero
     *     character or an unpaired surrogate
     */
    public RequestAuthLogin encrypt(PublicKey key, Layout layout) throws GeneralSecurityException {
        final byte[] userBytes = utf8("user", user);
        final byte[] passwordBytes = utf8("password", password);
        require("password", passwordBytes, PASSWORD_SIZE);
        final byte[] plain;
        if (layout == Layout.TWO_BLOCKS) {
            require("user", userBytes, TWO_BLOCKS_USER_SIZE + TWO_BLOCKS_USER_TAIL_SIZE);
            plain = new byte[2 * BLOCK];
            final int head = Math.min(userBytes.length, TWO_BLOCKS_USER_SIZE);
            System.arraycopy(userBytes, 0, plain, TWO_BLOCKS_USER, head);
            System.arraycopy(userBytes, head, plain, TWO_BLOCKS_USER_TAIL, userBytes.length - head);
            System.arraycopy(passwordBytes, 0, plain, TWO_BLOCKS_PASSWORD, passwordBytes.length);
        } else {
            require("user", userBytes, ONE_BLOCK_USER_SIZE);
            plain = new byte[BLOCK];
            System.arraycopy(userBytes, 0, plain, ONE_BLOCK_USER, userBytes.length);
            System.arraycopy(passwordBytes, 0, plain, ONE_BLOCK_PASSWORD, passwordBytes.length);
        }

        final Cipher rsa = Cipher.getInstance(TRANSFORMATION);
        rsa.init(Cipher.ENCRYPT_MODE, key);
        final RequestAuthLogin.RequestAuthLoginBuilder packet =
                RequestAuthLogin.builder().block1(rsa.doFinal(plain, 0, BLOCK));
        if (layout == Layout.TWO_BLOCKS) {
            packet.block2(rsa.doFinal(plain, BLOCK, BLOCK));
        }
        return packet.build();
    }

    /** Never prints the password. */
    @Override
    public String toString() {
        return "Credentials[user=" + user + "]";
    }

    private static String field(byte[] bytes, int offset, int size) {
        int length = 0;
        while ((length < size) && (bytes[offset + length] != 0)) {
            length++;
        }
        final String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString()
                    .trim();
        } catch (CharacterCodingException e) {
            // Not UTF-8 (a CP1251 login, a garbled block): no account can match it, so the field reads as missing.
            return null;
        }
        return text.isEmpty() ? null : text;
    }

    private static byte[] utf8(String name, String text) {
        if (text == null) {
            return new byte[0];
        }
        // A zero would end the field early on the other side.
        if (text.indexOf(0) >= 0) {
            throw new IllegalArgumentException("The " + name + " holds a zero character");
        }
        try {
            final ByteBuffer encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            final byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("The " + name + " is not valid UTF-16 text", e);
        }
    }

    private static void require(String name, byte[] value, int size) {
        if (value.length > size) {
            throw new IllegalArgumentException("The " + name + " takes up to " + size + " bytes, not " + value.length);
        }
    }
}
