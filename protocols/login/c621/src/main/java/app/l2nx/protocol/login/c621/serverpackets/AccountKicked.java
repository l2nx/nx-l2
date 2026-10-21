/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.login.c621.serverpackets.reason.AccountKickedReason;
import io.netty.buffer.ByteBuf;

public record AccountKicked(AccountKickedReason reason) implements LoginServerPacket {
    private static final int BODY = 4;

    public static AccountKicked read(ByteBuf body) {
        if (body.readableBytes() < BODY) {
            throw new PacketDecodeException(
                    "AccountKicked needs " + BODY + " bytes, " + body.readableBytes() + " in the frame");
        }
        final int code = body.readIntLE();
        final AccountKickedReason reason = AccountKickedReason.of(code);
        if (reason == null) {
            throw new PacketDecodeException("Unknown AccountKicked reason 0x" + Integer.toHexString(code));
        }
        return new AccountKicked(reason);
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(LoginServerOpcode.ACCOUNT_KICKED.opcode());
        out.writeIntLE(reason.code());
    }
}
