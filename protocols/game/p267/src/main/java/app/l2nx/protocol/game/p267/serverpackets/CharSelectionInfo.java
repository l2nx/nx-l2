/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * @param maxCharacters how many characters the account may have; 0 keeps the client from offering creation
 */
@Builder(toBuilder = true)
public record CharSelectionInfo(int maxCharacters, List<Slot> slots) implements GameServerPacket {
    public static final int PAPERDOLL_SLOTS = 26;

    private static final int AFTER_KILLS = 7 * 4;
    // Transformation, pet NPC, pet level, pet food, pet food level as ints, pet HP and MP as doubles.
    private static final int PET = (5 * 4) + (2 * 8);
    static final int MIN_SLOT_BYTES = 317;

    public CharSelectionInfo {
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    /**
     * @param paperdollItemIds the item ids worn, in the client's {@link #PAPERDOLL_SLOTS} paperdoll order
     * @param expPercent the share of the current level's experience, 0 to 1
     * @param deleteSeconds -1 for a banned character, 0 for none pending, else the seconds until deletion
     */
    @Builder(toBuilder = true)
    public record Slot(
            String name,
            int objectId,
            String login,
            int playOk1,
            int clanId,
            int sex,
            int race,
            int baseClassId,
            int x,
            int y,
            int z,
            double currentHp,
            double currentMp,
            int sp,
            long exp,
            double expPercent,
            int level,
            int karma,
            int pkKills,
            int pvpKills,
            List<Integer> paperdollItemIds,
            int hairStyle,
            int hairColor,
            int face,
            double maxHp,
            double maxMp,
            int deleteSeconds,
            int classId,
            boolean active,
            int enchantEffect,
            int augmentationId,
            int vitalityPoints) {
        public Slot {
            paperdollItemIds = List.copyOf(Objects.requireNonNull(paperdollItemIds, "paperdollItemIds"));
            if (paperdollItemIds.size() != PAPERDOLL_SLOTS) {
                throw new IllegalArgumentException(
                        "A paperdoll has " + PAPERDOLL_SLOTS + " slots, not " + paperdollItemIds.size());
            }
        }
    }

    public static CharSelectionInfo read(ByteBuf body) {
        return Bodies.parse("CharSelectionInfo", body, in -> {
            final int count = in.readIntLE();
            final int maxCharacters = in.readIntLE();
            in.skipBytes(1);
            final List<Slot> slots = new ArrayList<>(Bodies.count(count, in, MIN_SLOT_BYTES));
            for (int i = 0; i < count; i++) {
                slots.add(readSlot(in));
            }
            return new CharSelectionInfo(maxCharacters, slots);
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.CHARACTER_SELECTION_INFO.opcode());
        out.writeIntLE(slots.size());
        out.writeIntLE(maxCharacters);
        out.writeByte(0);
        for (Slot slot : slots) {
            Wire.writeString(out, slot.name());
            out.writeIntLE(slot.objectId());
            Wire.writeString(out, slot.login());
            out.writeIntLE(slot.playOk1());
            out.writeIntLE(slot.clanId());
            out.writeIntLE(0); // builder level
            out.writeIntLE(slot.sex());
            out.writeIntLE(slot.race());
            out.writeIntLE(slot.baseClassId());
            out.writeIntLE(1); // game server name
            out.writeIntLE(slot.x());
            out.writeIntLE(slot.y());
            out.writeIntLE(slot.z());
            out.writeDoubleLE(slot.currentHp());
            out.writeDoubleLE(slot.currentMp());
            out.writeIntLE(slot.sp());
            out.writeLongLE(slot.exp());
            out.writeDoubleLE(slot.expPercent());
            out.writeIntLE(slot.level());
            out.writeIntLE(slot.karma());
            out.writeIntLE(slot.pkKills());
            out.writeIntLE(slot.pvpKills());
            out.writeZero(AFTER_KILLS);
            for (int itemId : slot.paperdollItemIds()) {
                out.writeIntLE(itemId);
            }
            out.writeIntLE(slot.hairStyle());
            out.writeIntLE(slot.hairColor());
            out.writeIntLE(slot.face());
            out.writeDoubleLE(slot.maxHp());
            out.writeDoubleLE(slot.maxMp());
            out.writeIntLE(slot.deleteSeconds());
            out.writeIntLE(slot.classId());
            out.writeIntLE(slot.active() ? 1 : 0);
            out.writeByte(slot.enchantEffect());
            out.writeIntLE(slot.augmentationId());
            out.writeZero(PET);
            out.writeIntLE(slot.vitalityPoints());
        }
    }

    private static Slot readSlot(ByteBuf in) {
        final Slot.SlotBuilder slot = Slot.builder()
                .name(Wire.readString(in))
                .objectId(in.readIntLE())
                .login(Wire.readString(in))
                .playOk1(in.readIntLE())
                .clanId(in.readIntLE());
        in.skipBytes(4);
        slot.sex(in.readIntLE()).race(in.readIntLE()).baseClassId(in.readIntLE());
        in.skipBytes(4);
        slot.x(in.readIntLE())
                .y(in.readIntLE())
                .z(in.readIntLE())
                .currentHp(in.readDoubleLE())
                .currentMp(in.readDoubleLE())
                .sp(in.readIntLE())
                .exp(in.readLongLE())
                .expPercent(in.readDoubleLE())
                .level(in.readIntLE())
                .karma(in.readIntLE())
                .pkKills(in.readIntLE())
                .pvpKills(in.readIntLE());
        in.skipBytes(AFTER_KILLS);
        final List<Integer> paperdoll = new ArrayList<>(PAPERDOLL_SLOTS);
        for (int i = 0; i < PAPERDOLL_SLOTS; i++) {
            paperdoll.add(in.readIntLE());
        }
        slot.paperdollItemIds(paperdoll)
                .hairStyle(in.readIntLE())
                .hairColor(in.readIntLE())
                .face(in.readIntLE())
                .maxHp(in.readDoubleLE())
                .maxMp(in.readDoubleLE())
                .deleteSeconds(in.readIntLE())
                .classId(in.readIntLE())
                .active(in.readIntLE() != 0)
                .enchantEffect(in.readUnsignedByte())
                .augmentationId(in.readIntLE());
        in.skipBytes(PET);
        return slot.vitalityPoints(in.readIntLE()).build();
    }
}
