/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.Bodies;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharDeleteFailReason;
import io.netty.buffer.ByteBuf;
import java.util.Objects;

public record CharDeleteFail(CharDeleteFailReason reason) implements GameServerPacket {
    public CharDeleteFail {
        Objects.requireNonNull(reason, "reason");
    }

    public static CharDeleteFail read(ByteBuf body) {
        final int code = Bodies.parse("CharDeleteFail", body, ByteBuf::readIntLE);
        final CharDeleteFailReason reason = CharDeleteFailReason.of(code);
        if (reason == null) {
            throw new PacketDecodeException("Unknown CharDeleteFail reason " + code);
        }
        return new CharDeleteFail(reason);
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.CHARACTER_DELETE_FAIL.opcode());
        out.writeIntLE(reason.code());
    }
}
