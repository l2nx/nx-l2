/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.crypt;

import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.RollingXor;
import app.l2nx.network.crypt.XorChecksum;
import io.netty.buffer.ByteBuf;
import java.util.function.IntSupplier;

/**
 * {@code Init} is the only frame under the static key every client knows; later frames use the session key and a
 * checksum. The server pads by at least one block plus eight bytes; the client pads to the block.
 */
public final class LoginFrameCipher implements FrameCipher {
    static final byte[] STATIC_KEY = {
        (byte) 0x6b, (byte) 0x60, (byte) 0xcb, (byte) 0x5b, (byte) 0x82, (byte) 0xce, (byte) 0x90, (byte) 0xb1,
        (byte) 0xcc, (byte) 0x2b, (byte) 0x6c, (byte) 0x55, (byte) 0x6c, (byte) 0x6c, (byte) 0x6c, (byte) 0x6c
    };

    // Shared by every connection; LittleEndianBlowfishCipher synchronizes each direction.
    private static final LittleEndianBlowfishCipher STATIC_CIPHER = new LittleEndianBlowfishCipher(STATIC_KEY);
    private static final int BLOCK = 8;
    private static final int STATIC_HEADER = 8;
    private static final int SESSION_HEADER = 4;
    private static final int TRAILER = 8;
    private static final int CHECKSUM = 4;
    // The XOR pass needs a plain first word, at least one XORed word, the key word and a last word.
    private static final int MIN_INIT = 2 * BLOCK;

    public static final int MAX_OVERHEAD = STATIC_HEADER + BLOCK + TRAILER;

    private final boolean server;
    private final IntSupplier xorKeys;
    private LittleEndianBlowfishCipher session;
    private boolean staticPhase = true;

    private LoginFrameCipher(boolean server, byte[] sessionKey, IntSupplier xorKeys) {
        this.server = server;
        this.xorKeys = xorKeys;
        session = sessionKey == null ? null : new LittleEndianBlowfishCipher(sessionKey);
    }

    /**
     * @param xorKeys draws the key of the XOR pass over {@code Init}
     */
    public static LoginFrameCipher server(byte[] sessionKey, IntSupplier xorKeys) {
        return new LoginFrameCipher(true, sessionKey, xorKeys);
    }

    /** Decrypts {@code Init} with the static key; the session key from it is set with {@link #sessionKey}. */
    public static LoginFrameCipher client() {
        return new LoginFrameCipher(false, null, null);
    }

    /** Client side: the key {@code Init} carried, set before the next frame in either direction. */
    public void sessionKey(byte[] key) {
        if (server) {
            throw new IllegalStateException("The server side gets its session key at construction");
        }
        session = new LittleEndianBlowfishCipher(key);
    }

    @Override
    public int encryptedLength(int payloadLength) {
        if (!server) {
            final int withChecksum = payloadLength + CHECKSUM;
            return withChecksum + ((BLOCK - (withChecksum % BLOCK)) % BLOCK);
        }
        final int withHeader = payloadLength + (staticPhase ? STATIC_HEADER : SESSION_HEADER);
        return withHeader + (BLOCK - (withHeader % BLOCK)) + TRAILER;
    }

    @Override
    public void encrypt(ByteBuf buf, int index, int payloadLength) {
        final int length = encryptedLength(payloadLength);
        buf.setZero(index + payloadLength, length - payloadLength);
        if (server && staticPhase) {
            RollingXor.encrypt(buf, index, length, xorKeys.getAsInt());
            STATIC_CIPHER.encrypt(buf, index, length);
            staticPhase = false;
            return;
        }
        XorChecksum.append(buf, index, length);
        session().encrypt(buf, index, length);
    }

    @Override
    public boolean decrypt(ByteBuf buf, int index, int length) {
        if ((length % BLOCK) != 0) {
            return false;
        }
        if (!server && staticPhase) {
            if (length < MIN_INIT) {
                return false;
            }
            STATIC_CIPHER.decrypt(buf, index, length);
            RollingXor.decrypt(buf, index, length);
            staticPhase = false;
            return true;
        }
        session().decrypt(buf, index, length);
        return XorChecksum.verify(buf, index, length);
    }

    private LittleEndianBlowfishCipher session() {
        if (session == null) {
            throw new IllegalStateException("No session key: Init has not been read");
        }
        return session;
    }
}
