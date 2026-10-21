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
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import io.netty.buffer.ByteBuf;

public record LoginFail(LoginFailReason reason) implements LoginServerPacket {
    public static LoginFail read(ByteBuf body) {
        if (!body.isReadable()) {
            throw new PacketDecodeException("LoginFail without its reason");
        }
        final int code = body.readUnsignedByte();
        final LoginFailReason reason = LoginFailReason.of(code);
        if (reason == null) {
            throw new PacketDecodeException("Unknown LoginFail reason 0x" + Integer.toHexString(code));
        }
        return new LoginFail(reason);
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(LoginServerOpcode.LOGIN_FAIL.opcode());
        out.writeByte(reason.code());
    }
}
