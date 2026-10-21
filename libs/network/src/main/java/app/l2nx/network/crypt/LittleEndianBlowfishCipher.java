/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Blowfish/ECB over little-endian 32-bit words: JCE reads block halves big-endian, so every word is byte-swapped around the JCE pass.
 * Thread-safe: each direction serializes on its own {@link Cipher}.
 */
public final class LittleEndianBlowfishCipher {
    private static final String ALGORITHM = "Blowfish";
    private static final String TRANSFORMATION = "Blowfish/ECB/NoPadding";
    private static final int BLOCK_SIZE = 8;
    private static final int WORD_SIZE = 4;

    private final Cipher encryptCipher;
    private final Cipher decryptCipher;

    /**
     * @param key 1 to 56 bytes: the JCE limit, though Blowfish itself takes up to 72
     */
    public LittleEndianBlowfishCipher(byte[] key) {
        final SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
        encryptCipher = newCipher(Cipher.ENCRYPT_MODE, keySpec);
        decryptCipher = newCipher(Cipher.DECRYPT_MODE, keySpec);
    }

    public void encrypt(byte[] data, int offset, int size) {
        process(encryptCipher, Unpooled.wrappedBuffer(data), offset, size);
    }

    public void decrypt(byte[] data, int offset, int size) {
        process(decryptCipher, Unpooled.wrappedBuffer(data), offset, size);
    }

    public void encrypt(ByteBuf buf, int index, int size) {
        process(encryptCipher, buf, index, size);
    }

    public void decrypt(ByteBuf buf, int index, int size) {
        process(decryptCipher, buf, index, size);
    }

    private static Cipher newCipher(int mode, SecretKeySpec keySpec) {
        final Cipher cipher;
        try {
            cipher = Cipher.getInstance(TRANSFORMATION);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(TRANSFORMATION + " is not available", e);
        }

        try {
            cipher.init(mode, keySpec);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid Blowfish key of " + keySpec.getEncoded().length + " bytes", e);
        }

        return cipher;
    }

    private static void process(Cipher cipher, ByteBuf buf, int index, int size) {
        Objects.checkFromIndexSize(index, size, buf.capacity());
        if ((size % BLOCK_SIZE) != 0) {
            throw new IllegalArgumentException(
                    "Size " + size + " is not a multiple of the Blowfish block size " + BLOCK_SIZE);
        }

        if (!buf.hasArray() && (buf.nioBufferCount() != 1)) {
            // A composite buffer has no single memory region to encrypt in place.
            final byte[] copy = ByteBufUtil.getBytes(buf, index, size);
            process(cipher, Unpooled.wrappedBuffer(copy), 0, size);
            buf.setBytes(index, copy);
            return;
        }

        swapWords(buf, index, size);
        synchronized (cipher) {
            try {
                if (buf.hasArray()) {
                    final int offset = buf.arrayOffset() + index;
                    cipher.doFinal(buf.array(), offset, size, buf.array(), offset);
                } else {
                    final ByteBuffer region = buf.nioBuffer(index, size);
                    // JCE ByteBuffer operations are copy-safe, so input and output may share the memory.
                    cipher.doFinal(region, region.duplicate());
                }
            } catch (GeneralSecurityException e) {
                // Unreachable: the region is block-aligned and NoPadding cannot fail on padding.
                throw new IllegalStateException(e);
            }
        }
        swapWords(buf, index, size);
    }

    private static void swapWords(ByteBuf buf, int index, int size) {
        final int end = index + size;
        for (int i = index; i < end; i += WORD_SIZE) {
            buf.setInt(i, buf.getIntLE(i));
        }
    }
}
