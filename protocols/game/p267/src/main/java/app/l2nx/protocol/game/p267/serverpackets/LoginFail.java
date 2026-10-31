/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.Bodies;
import app.l2nx.protocol.game.p267.serverpackets.reason.LoginFailReason;
import io.netty.buffer.ByteBuf;
import java.util.Objects;

public record LoginFail(LoginFailReason reason) implements GameServerPacket {
    public LoginFail {
        Objects.requireNonNull(reason, "reason");
    }

    public static LoginFail read(ByteBuf body) {
        final int code = Bodies.parse("LoginFail", body, ByteBuf::readIntLE);
        final LoginFailReason reason = LoginFailReason.of(code);
        if (reason == null) {
            throw new PacketDecodeException("Unknown LoginFail reason " + code);
        }
        return new LoginFail(reason);
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.LOGIN_FAIL.opcode());
        out.writeIntLE(reason.code());
    }
}
