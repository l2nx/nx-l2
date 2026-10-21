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
package app.l2nx.protocol.c621.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.c621.crypt.ScrambledModulus;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Objects;
import lombok.Builder;

/**
 * The first packet, sent by the server unasked.
 *
 * @param blowfishKey the session key; the client reads it up to the first zero byte, so it holds none
 */
@Builder(toBuilder = true)
public record Init(int sessionId, byte[] scrambledModulus, byte[] blowfishKey) implements LoginServerPacket {
    public static final int PROTOCOL_REVISION = 0x0000C621;

    private static final int[] GAME_GUARD = {0x29DD954E, 0x77C39CFC, 0x97ADB620, 0x07BDE0F7};
    private static final int FIXED_BODY = 4 + 4 + ScrambledModulus.SIZE + (4 * GAME_GUARD.length);

    public Init {
        Objects.requireNonNull(scrambledModulus, "scrambledModulus");
        Objects.requireNonNull(blowfishKey, "blowfishKey");
        if (scrambledModulus.length != ScrambledModulus.SIZE) {
            throw new IllegalArgumentException(
                    "A scrambled modulus has " + ScrambledModulus.SIZE + " bytes, not " + scrambledModulus.length);
        }
        scrambledModulus = scrambledModulus.clone();
        blowfishKey = blowfishKey.clone();
    }

    public static Init read(ByteBuf body) {
        if (body.readableBytes() < FIXED_BODY + 1) {
            throw new PacketDecodeException(
                    "Init needs " + (FIXED_BODY + 1) + " bytes, " + body.readableBytes() + " in the frame");
        }
        final int sessionId = body.readIntLE();
        final int revision = body.readIntLE();
        if (revision != PROTOCOL_REVISION) {
            throw new PacketDecodeException("Init of protocol revision 0x" + Integer.toHexString(revision));
        }
        final byte[] modulus = new byte[ScrambledModulus.SIZE];
        body.readBytes(modulus);
        body.skipBytes(4 * GAME_GUARD.length);
        final int keyLength = body.bytesBefore((byte) 0);
        if (keyLength <= 0) {
            throw new PacketDecodeException("Init without a terminated Blowfish key");
        }
        final byte[] key = new byte[keyLength];
        body.readBytes(key);
        body.skipBytes(1);
        return Init.builder()
                .sessionId(sessionId)
                .scrambledModulus(modulus)
                .blowfishKey(key)
                .build();
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(LoginServerOpcode.INIT.opcode());
        out.writeIntLE(sessionId);
        out.writeIntLE(PROTOCOL_REVISION);
        out.writeBytes(scrambledModulus);
        for (int word : GAME_GUARD) {
            out.writeIntLE(word);
        }
        out.writeBytes(blowfishKey);
        out.writeByte(0);
    }

    @Override
    public byte[] scrambledModulus() {
        return scrambledModulus.clone();
    }

    @Override
    public byte[] blowfishKey() {
        return blowfishKey.clone();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof Init that)
                && (sessionId == that.sessionId)
                && Arrays.equals(scrambledModulus, that.scrambledModulus)
                && Arrays.equals(blowfishKey, that.blowfishKey);
    }

    @Override
    public int hashCode() {
        return (31 * ((31 * Integer.hashCode(sessionId)) + Arrays.hashCode(scrambledModulus)))
                + Arrays.hashCode(blowfishKey);
    }

    /** Never prints the session key. */
    @Override
    public String toString() {
        return "Init[sessionId=" + sessionId + "]";
    }
}
