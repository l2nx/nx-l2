/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record StopMove(int objectId, int x, int y, int z, int heading) implements GameServerPacket {
    public static StopMove read(ByteBuf body) {
        return Bodies.parse(
                "StopMove",
                body,
                in -> StopMove.builder()
                        .objectId(in.readIntLE())
                        .x(in.readIntLE())
                        .y(in.readIntLE())
                        .z(in.readIntLE())
                        .heading(in.readIntLE())
                        .build());
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.STOP_MOVE.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeIntLE(heading);
    }
}
