/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record MyTargetSelected(int objectId, int color) implements GameServerPacket {
    public static MyTargetSelected read(ByteBuf body) {
        return Bodies.parse("MyTargetSelected", body, in -> {
            final MyTargetSelected packet = MyTargetSelected.builder()
                    .objectId(in.readIntLE())
                    .color(in.readShortLE())
                    .build();
            in.skipBytes(4);
            return packet;
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.MY_TARGET_SELECTED.opcode());
        out.writeIntLE(objectId);
        out.writeShortLE(color);
        out.writeIntLE(0);
    }
}
