/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record Revive(int objectId) implements GameServerPacket {
    public static Revive read(ByteBuf body) {
        return Bodies.parse("Revive", body, in -> new Revive(in.readIntLE()));
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.REVIVE.opcode());
        out.writeIntLE(objectId);
    }
}
