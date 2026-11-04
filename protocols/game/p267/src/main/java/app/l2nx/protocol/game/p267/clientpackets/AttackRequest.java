/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record AttackRequest(int objectId, int originX, int originY, int originZ, int attackId) {
    public static AttackRequest decode(ByteBuf body) {
        return Bodies.parse(
                "AttackRequest",
                body,
                in -> AttackRequest.builder()
                        .objectId(in.readIntLE())
                        .originX(in.readIntLE())
                        .originY(in.readIntLE())
                        .originZ(in.readIntLE())
                        .attackId(in.readByte())
                        .build());
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.ATTACK_REQUEST.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(originX);
        out.writeIntLE(originY);
        out.writeIntLE(originZ);
        out.writeByte(attackId);
    }
}
