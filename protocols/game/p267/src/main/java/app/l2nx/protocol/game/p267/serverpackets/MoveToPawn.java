/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record MoveToPawn(
        int objectId, int targetObjectId, int distance, int x, int y, int z, int targetX, int targetY, int targetZ)
        implements GameServerPacket {
    public static MoveToPawn read(ByteBuf body) {
        return Bodies.parse(
                "MoveToPawn",
                body,
                in -> MoveToPawn.builder()
                        .objectId(in.readIntLE())
                        .targetObjectId(in.readIntLE())
                        .distance(in.readIntLE())
                        .x(in.readIntLE())
                        .y(in.readIntLE())
                        .z(in.readIntLE())
                        .targetX(in.readIntLE())
                        .targetY(in.readIntLE())
                        .targetZ(in.readIntLE())
                        .build());
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.MOVE_TO_PAWN.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(targetObjectId);
        out.writeIntLE(distance);
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeIntLE(targetX);
        out.writeIntLE(targetY);
        out.writeIntLE(targetZ);
    }
}
