/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.network.codec.Wire;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/** A system message argument: its wire type code, then its value. Codes 8, 14 and 15 are not used by High Five. */
public sealed interface SystemMessageParam {
    int TEXT = 0;
    int INT_NUMBER = 1;
    int NPC_NAME = 2;
    int ITEM_NAME = 3;
    int SKILL_NAME = 4;
    int CASTLE_NAME = 5;
    int LONG_NUMBER = 6;
    int ZONE_NAME = 7;
    int ELEMENT_NAME = 9;
    int INSTANCE_NAME = 10;
    int DOOR_NAME = 11;
    int PLAYER_NAME = 12;
    int SYSTEM_STRING = 13;

    /** The type code and the shortest value, an empty string. */
    int MIN_BYTES = 4 + 2;

    int type();

    void writeValue(ByteBuf out);

    static SystemMessageParam read(ByteBuf in) {
        final int type = in.readIntLE();
        return switch (type) {
            case TEXT -> new Text(Wire.readString(in));
            case INT_NUMBER -> new IntNumber(in.readIntLE());
            case NPC_NAME -> new NpcName(in.readIntLE());
            case ITEM_NAME -> new ItemName(in.readIntLE());
            case SKILL_NAME ->
                SkillName.builder().id(in.readIntLE()).level(in.readIntLE()).build();
            case CASTLE_NAME -> new CastleName(in.readIntLE());
            case LONG_NUMBER -> new LongNumber(in.readLongLE());
            case ZONE_NAME ->
                ZoneName.builder()
                        .x(in.readIntLE())
                        .y(in.readIntLE())
                        .z(in.readIntLE())
                        .build();
            case ELEMENT_NAME -> new ElementName(in.readIntLE());
            case INSTANCE_NAME -> new InstanceName(in.readIntLE());
            case DOOR_NAME -> new DoorName(in.readIntLE());
            case PLAYER_NAME -> new PlayerName(Wire.readString(in));
            case SYSTEM_STRING -> new SystemString(in.readIntLE());
            default -> throw new PacketDecodeException("Unknown SystemMessage parameter type " + type);
        };
    }

    /**
     * @param value {@code null} reaches the client as an empty string
     */
    record Text(String value) implements SystemMessageParam {
        @Override
        public int type() {
            return TEXT;
        }

        @Override
        public void writeValue(ByteBuf out) {
            Wire.writeString(out, value);
        }
    }

    record IntNumber(int value) implements SystemMessageParam {
        @Override
        public int type() {
            return INT_NUMBER;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(value);
        }
    }

    record NpcName(int npcId) implements SystemMessageParam {
        @Override
        public int type() {
            return NPC_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(npcId);
        }
    }

    record ItemName(int itemId) implements SystemMessageParam {
        @Override
        public int type() {
            return ITEM_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(itemId);
        }
    }

    @Builder(toBuilder = true)
    record SkillName(int id, int level) implements SystemMessageParam {
        @Override
        public int type() {
            return SKILL_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(id);
            out.writeIntLE(level);
        }
    }

    record CastleName(int castleId) implements SystemMessageParam {
        @Override
        public int type() {
            return CASTLE_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(castleId);
        }
    }

    record LongNumber(long value) implements SystemMessageParam {
        @Override
        public int type() {
            return LONG_NUMBER;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeLongLE(value);
        }
    }

    @Builder(toBuilder = true)
    record ZoneName(int x, int y, int z) implements SystemMessageParam {
        @Override
        public int type() {
            return ZONE_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(x);
            out.writeIntLE(y);
            out.writeIntLE(z);
        }
    }

    record ElementName(int element) implements SystemMessageParam {
        @Override
        public int type() {
            return ELEMENT_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(element);
        }
    }

    record InstanceName(int instanceId) implements SystemMessageParam {
        @Override
        public int type() {
            return INSTANCE_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(instanceId);
        }
    }

    record DoorName(int doorId) implements SystemMessageParam {
        @Override
        public int type() {
            return DOOR_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(doorId);
        }
    }

    /**
     * @param name {@code null} reaches the client as an empty string
     */
    record PlayerName(String name) implements SystemMessageParam {
        @Override
        public int type() {
            return PLAYER_NAME;
        }

        @Override
        public void writeValue(ByteBuf out) {
            Wire.writeString(out, name);
        }
    }

    record SystemString(int stringId) implements SystemMessageParam {
        @Override
        public int type() {
            return SYSTEM_STRING;
        }

        @Override
        public void writeValue(ByteBuf out) {
            out.writeIntLE(stringId);
        }
    }
}
