/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record Die(
        int objectId,
        boolean canTeleport,
        boolean toClanHall,
        boolean toCastle,
        boolean toSiegeFlag,
        boolean sweepable,
        boolean toFixed,
        boolean toFortress)
        implements GameServerPacket {
    public static Die read(ByteBuf body) {
        return Bodies.parse(
                "Die",
                body,
                in -> Die.builder()
                        .objectId(in.readIntLE())
                        .canTeleport(in.readIntLE() != 0)
                        .toClanHall(in.readIntLE() != 0)
                        .toCastle(in.readIntLE() != 0)
                        .toSiegeFlag(in.readIntLE() != 0)
                        .sweepable(in.readIntLE() != 0)
                        .toFixed(in.readIntLE() != 0)
                        .toFortress(in.readIntLE() != 0)
                        .build());
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.DIE.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(canTeleport ? 1 : 0);
        out.writeIntLE(toClanHall ? 1 : 0);
        out.writeIntLE(toCastle ? 1 : 0);
        out.writeIntLE(toSiegeFlag ? 1 : 0);
        out.writeIntLE(sweepable ? 1 : 0);
        out.writeIntLE(toFixed ? 1 : 0);
        out.writeIntLE(toFortress ? 1 : 0);
    }
}
