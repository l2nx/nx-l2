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
package app.l2nx.protocol.login.c621.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/** The LoginOk pair of the session key, followed by reserved fields the client does not use. */
@Builder(toBuilder = true)
public record LoginOk(int loginOk1, int loginOk2) implements LoginServerPacket {
    private static final int RESERVED_WORD = 0x000003EA;
    private static final int RESERVED_BYTES = 16;
    private static final int BODY = 8 + (6 * 4) + RESERVED_BYTES;

    public static LoginOk read(ByteBuf body) {
        if (body.readableBytes() < BODY) {
            throw new PacketDecodeException(
                    "LoginOk needs " + BODY + " bytes, " + body.readableBytes() + " in the frame");
        }
        final LoginOk packet = LoginOk.builder()
                .loginOk1(body.readIntLE())
                .loginOk2(body.readIntLE())
                .build();
        body.skipBytes(BODY - 8);
        return packet;
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(LoginServerOpcode.LOGIN_OK.opcode());
        out.writeIntLE(loginOk1);
        out.writeIntLE(loginOk2);
        out.writeIntLE(0);
        out.writeIntLE(0);
        out.writeIntLE(RESERVED_WORD);
        out.writeIntLE(0);
        out.writeIntLE(0);
        out.writeIntLE(0);
        out.writeZero(RESERVED_BYTES);
    }
}
