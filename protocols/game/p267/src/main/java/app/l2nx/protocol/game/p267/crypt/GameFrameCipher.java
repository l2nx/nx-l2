/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.crypt;

import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.crypt.GameXorCipher;
import io.netty.buffer.ByteBuf;

/**
 * On the server side the key packet itself stays clear: the first frame encoded after {@link #setKey} switches the
 * rolling XOR on for both directions, so inbound frames sent before the client had the key also pass in clear.
 */
public final class GameFrameCipher implements FrameCipher {
    private final GameXorCipher xor = new GameXorCipher();
    private final boolean clientSide;
    private boolean keyed;
    private boolean enabled;

    public GameFrameCipher() {
        this(false);
    }

    private GameFrameCipher(boolean clientSide) {
        this.clientSide = clientSide;
    }

    public static GameFrameCipher client() {
        return new GameFrameCipher(true);
    }

    public void setKey(byte[] key) {
        xor.setKey(key);
        keyed = true;
        if (clientSide) {
            enabled = true;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int encryptedLength(int payloadLength) {
        return payloadLength;
    }

    @Override
    public void encrypt(ByteBuf buf, int index, int payloadLength) {
        if (!keyed) {
            return;
        }
        if (!enabled) {
            enabled = true;
            return;
        }
        xor.encrypt(buf, index, payloadLength);
    }

    @Override
    public boolean decrypt(ByteBuf buf, int index, int length) {
        if (enabled) {
            xor.decrypt(buf, index, length);
        }
        return true;
    }
}
