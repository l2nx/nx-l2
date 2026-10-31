/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record CannotMoveAnymore(int x, int y, int z, int heading) {
    public static CannotMoveAnymore decode(ByteBuf body) {
        return Bodies.parse(
                "CannotMoveAnymore",
                body,
                in -> CannotMoveAnymore.builder()
                        .x(in.readIntLE())
                        .y(in.readIntLE())
                        .z(in.readIntLE())
                        .heading(in.readIntLE())
                        .build());
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.CANNOT_MOVE_ANYMORE.opcode());
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeIntLE(heading);
    }
}
