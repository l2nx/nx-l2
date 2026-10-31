/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record CharSelected(
        String name,
        int objectId,
        String title,
        int playOk1,
        int clanId,
        boolean female,
        int race,
        int classId,
        int x,
        int y,
        int z,
        double currentHp,
        double currentMp,
        int sp,
        long exp,
        int level,
        int karma,
        int pkKills,
        int intelligence,
        int strength,
        int constitution,
        int mentality,
        int dexterity,
        int wit,
        int gameMinute)
        implements GameServerPacket {
    // After the game minute: an unknown int, the class id again, four unknown ints, 64 zero bytes, an unknown int.
    private static final int TAIL = 4 + 4 + 16 + 64 + 4;

    public static CharSelected read(ByteBuf body) {
        return Bodies.parse("CharSelected", body, in -> {
            final CharSelectedBuilder packet = CharSelected.builder()
                    .name(Wire.readString(in))
                    .objectId(in.readIntLE())
                    .title(Wire.readString(in))
                    .playOk1(in.readIntLE())
                    .clanId(in.readIntLE());
            in.skipBytes(4);
            packet.female(in.readIntLE() != 0).race(in.readIntLE()).classId(in.readIntLE());
            in.skipBytes(4);
            packet.x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE())
                    .currentHp(in.readDoubleLE())
                    .currentMp(in.readDoubleLE())
                    .sp(in.readIntLE())
                    .exp(in.readLongLE())
                    .level(in.readIntLE())
                    .karma(in.readIntLE())
                    .pkKills(in.readIntLE())
                    .intelligence(in.readIntLE())
                    .strength(in.readIntLE())
                    .constitution(in.readIntLE())
                    .mentality(in.readIntLE())
                    .dexterity(in.readIntLE())
                    .wit(in.readIntLE())
                    .gameMinute(in.readIntLE());
            in.skipBytes(TAIL);
            return packet.build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.CHARACTER_SELECTED.opcode());
        Wire.writeString(out, name);
        out.writeIntLE(objectId);
        Wire.writeString(out, title);
        out.writeIntLE(playOk1);
        out.writeIntLE(clanId);
        out.writeIntLE(0);
        out.writeIntLE(female ? 1 : 0);
        out.writeIntLE(race);
        out.writeIntLE(classId);
        out.writeIntLE(1);
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeDoubleLE(currentHp);
        out.writeDoubleLE(currentMp);
        out.writeIntLE(sp);
        out.writeLongLE(exp);
        out.writeIntLE(level);
        out.writeIntLE(karma);
        out.writeIntLE(pkKills);
        out.writeIntLE(intelligence);
        out.writeIntLE(strength);
        out.writeIntLE(constitution);
        out.writeIntLE(mentality);
        out.writeIntLE(dexterity);
        out.writeIntLE(wit);
        out.writeIntLE(gameMinute);
        out.writeIntLE(0);
        out.writeIntLE(classId);
        out.writeZero(16);
        out.writeZero(64);
        out.writeIntLE(0);
    }
}
