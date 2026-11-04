/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record TeleportToLocation(int objectId, int x, int y, int z, int heading) implements GameServerPacket {
    private static final int FADE = 0;

    public static TeleportToLocation read(ByteBuf body) {
        return Bodies.parse("TeleportToLocation", body, in -> {
            final TeleportToLocationBuilder packet = TeleportToLocation.builder()
                    .objectId(in.readIntLE())
                    .x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE());
            in.skipBytes(4);
            return packet.heading(in.readIntLE()).build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.TELEPORT_TO_LOCATION.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeIntLE(FADE);
        out.writeIntLE(heading);
    }
}
