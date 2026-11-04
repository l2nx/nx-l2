/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record DeleteObject(int objectId) implements GameServerPacket {
    private static final int DISAPPEAR = 1;

    public static DeleteObject read(ByteBuf body) {
        return Bodies.parse("DeleteObject", body, in -> {
            final DeleteObject packet = new DeleteObject(in.readIntLE());
            in.skipBytes(4);
            return packet;
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.DELETE_OBJECT.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(DISAPPEAR);
    }
}
