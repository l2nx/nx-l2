/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.Bodies;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharCreateFailReason;
import io.netty.buffer.ByteBuf;
import java.util.Objects;

public record CharCreateFail(CharCreateFailReason reason) implements GameServerPacket {
    public CharCreateFail {
        Objects.requireNonNull(reason, "reason");
    }

    public static CharCreateFail read(ByteBuf body) {
        final int code = Bodies.parse("CharCreateFail", body, ByteBuf::readIntLE);
        final CharCreateFailReason reason = CharCreateFailReason.of(code);
        if (reason == null) {
            throw new PacketDecodeException("Unknown CharCreateFail reason " + code);
        }
        return new CharCreateFail(reason);
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.CHARACTER_CREATE_FAIL.opcode());
        out.writeIntLE(reason.code());
    }
}
