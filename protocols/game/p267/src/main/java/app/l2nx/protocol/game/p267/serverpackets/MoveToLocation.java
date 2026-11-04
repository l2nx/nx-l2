/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record MoveToLocation(int objectId, int targetX, int targetY, int targetZ, int originX, int originY, int originZ)
        implements GameServerPacket {
    public static MoveToLocation read(ByteBuf body) {
        return Bodies.parse(
                "MoveToLocation",
                body,
                in -> MoveToLocation.builder()
                        .objectId(in.readIntLE())
                        .targetX(in.readIntLE())
                        .targetY(in.readIntLE())
                        .targetZ(in.readIntLE())
                        .originX(in.readIntLE())
                        .originY(in.readIntLE())
                        .originZ(in.readIntLE())
                        .build());
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.MOVE_TO_LOCATION.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(targetX);
        out.writeIntLE(targetY);
        out.writeIntLE(targetZ);
        out.writeIntLE(originX);
        out.writeIntLE(originY);
        out.writeIntLE(originZ);
    }
}
