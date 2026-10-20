/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import app.l2nx.network.codec.FrameCipher;
import io.netty.buffer.ByteBuf;

public final class XorTestCipher implements FrameCipher {
    private final int padTo;

    public XorTestCipher(int padTo) {
        this.padTo = padTo;
    }

    @Override
    public int encryptedLength(int payloadLength) {
        return padTo <= 1 ? payloadLength : ((payloadLength + padTo - 1) / padTo) * padTo;
    }

    @Override
    public void encrypt(ByteBuf buf, int index, int payloadLength) {
        final int length = encryptedLength(payloadLength);
        buf.setZero(index + payloadLength, length - payloadLength);
        for (int i = index; i < index + length; i++) {
            buf.setByte(i, buf.getByte(i) ^ 0x5A);
        }
    }

    @Override
    public boolean decrypt(ByteBuf buf, int index, int length) {
        for (int i = index; i < index + length; i++) {
            buf.setByte(i, buf.getByte(i) ^ 0x5A);
        }
        return (length == 0) || (buf.getUnsignedByte(index) != 0xFF);
    }
}
