/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record MoveToLocation(
        int targetX, int targetY, int targetZ, int originX, int originY, int originZ, int movementMode) {
    public static final int MOUSE = 1;

    public static MoveToLocation decode(ByteBuf body) {
        return Bodies.parse(
                "MoveToLocation",
                body,
                in -> MoveToLocation.builder()
                        .targetX(in.readIntLE())
                        .targetY(in.readIntLE())
                        .targetZ(in.readIntLE())
                        .originX(in.readIntLE())
                        .originY(in.readIntLE())
                        .originZ(in.readIntLE())
                        .movementMode(in.readIntLE())
                        .build());
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.MOVE_TO_LOCATION.opcode());
        out.writeIntLE(targetX);
        out.writeIntLE(targetY);
        out.writeIntLE(targetZ);
        out.writeIntLE(originX);
        out.writeIntLE(originY);
        out.writeIntLE(originZ);
        out.writeIntLE(movementMode);
    }
}
