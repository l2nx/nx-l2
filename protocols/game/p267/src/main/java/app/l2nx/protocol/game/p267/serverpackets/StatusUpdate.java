/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

@Builder(toBuilder = true)
public record StatusUpdate(int objectId, List<Attribute> attributes) implements GameServerPacket {
    public static final int LEVEL = 0x01;
    public static final int EXP = 0x02;
    public static final int STR = 0x03;
    public static final int DEX = 0x04;
    public static final int CON = 0x05;
    public static final int INT = 0x06;
    public static final int WIT = 0x07;
    public static final int MEN = 0x08;
    public static final int CUR_HP = 0x09;
    public static final int MAX_HP = 0x0a;
    public static final int CUR_MP = 0x0b;
    public static final int MAX_MP = 0x0c;
    public static final int SP = 0x0d;
    public static final int CUR_LOAD = 0x0e;
    public static final int MAX_LOAD = 0x0f;
    public static final int P_ATK = 0x11;
    public static final int ATK_SPD = 0x12;
    public static final int P_DEF = 0x13;
    public static final int EVASION = 0x14;
    public static final int ACCURACY = 0x15;
    public static final int CRITICAL = 0x16;
    public static final int M_ATK = 0x17;
    public static final int CAST_SPD = 0x18;
    public static final int M_DEF = 0x19;
    public static final int PVP_FLAG = 0x1a;
    public static final int KARMA = 0x1b;
    public static final int CUR_CP = 0x21;
    public static final int MAX_CP = 0x22;

    private static final int ATTRIBUTE_BYTES = 4 + 4;

    public StatusUpdate {
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }

    public static StatusUpdate read(ByteBuf body) {
        return Bodies.parse("StatusUpdate", body, in -> {
            final StatusUpdateBuilder packet = StatusUpdate.builder().objectId(in.readIntLE());
            final int count = Bodies.count(in.readIntLE(), in, ATTRIBUTE_BYTES);
            final List<Attribute> attributes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                attributes.add(Attribute.builder()
                        .id(in.readIntLE())
                        .value(in.readIntLE())
                        .build());
            }
            return packet.attributes(attributes).build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.STATUS_UPDATE.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(attributes.size());
        for (Attribute attribute : attributes) {
            out.writeIntLE(attribute.id());
            out.writeIntLE(attribute.value());
        }
    }

    @Builder(toBuilder = true)
    public record Attribute(int id, int value) {}
}
