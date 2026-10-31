/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record CharCreateOk() implements GameServerPacket {
    public static final CharCreateOk INSTANCE = new CharCreateOk();

    public static CharCreateOk read(ByteBuf body) {
        Bodies.parse("CharCreateOk", body, ByteBuf::readIntLE);
        return INSTANCE;
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.CHARACTER_CREATE_SUCCESS.opcode());
        out.writeIntLE(1);
    }
}
