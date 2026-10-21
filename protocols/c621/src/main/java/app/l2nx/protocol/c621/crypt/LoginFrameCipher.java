/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.RollingXor;
import app.l2nx.network.crypt.XorChecksum;
import io.netty.buffer.ByteBuf;
import java.util.function.IntSupplier;

/**
 * The first frame the server sends ({@code Init}, which carries the session key) can only use the key every client
 * knows; every later frame uses the session key and a checksum. Padding always adds at least one block plus eight
 * bytes.
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

    private final LittleEndianBlowfishCipher session;
    private final IntSupplier xorKeys;
    private boolean staticPhase = true;

    public LoginFrameCipher(byte[] sessionKey, IntSupplier xorKeys) {
        session = new LittleEndianBlowfishCipher(sessionKey);
        this.xorKeys = xorKeys;
    }

    @Override
    public int encryptedLength(int payloadLength) {
        final int withHeader = payloadLength + (staticPhase ? STATIC_HEADER : SESSION_HEADER);
        return withHeader + (BLOCK - (withHeader % BLOCK)) + TRAILER;
    }

    @Override
    public void encrypt(ByteBuf buf, int index, int payloadLength) {
        final int length = encryptedLength(payloadLength);
        buf.setZero(index + payloadLength, length - payloadLength);
        if (staticPhase) {
            RollingXor.encrypt(buf, index, length, xorKeys.getAsInt());
            STATIC_CIPHER.encrypt(buf, index, length);
            staticPhase = false;
            return;
        }
        XorChecksum.append(buf, index, length);
        session.encrypt(buf, index, length);
    }

    @Override
    public boolean decrypt(ByteBuf buf, int index, int length) {
        if ((length % BLOCK) != 0) {
            return false;
        }
        session.decrypt(buf, index, length);
        return XorChecksum.verify(buf, index, length);
    }
}
