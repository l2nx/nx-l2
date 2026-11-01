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

/**
 * One item as the client's item windows read it, written from plain values: an inventory item or the snapshot of a
 * mail attachment.
 *
 * @param type2 the client's item class: 0 weapon, 1 shield or armour, 2 jewellery, 3 quest item, 4 adena, 5 other
 * @param mana {@link #NO_MANA} for an item without mana
 * @param remainingSeconds {@link #NO_TIME_LIMIT} for an item without a time limit
 * @param attackElementType {@link #NO_ATTACK_ELEMENT} for an item without an attack attribute
 * @param elementDefence the defence against each of the {@link #DEFENCE_ELEMENTS} attributes, in the client's order
 */
@Builder(toBuilder = true)
public record ItemBlock(
        int objectId,
        int displayId,
        int location,
        long count,
        int type2,
        int customType1,
        boolean equipped,
        int bodyPart,
        int enchantLevel,
        int customType2,
        int augmentationId,
        int mana,
        int remainingSeconds,
        int attackElementType,
        int attackElementPower,
        List<Integer> elementDefence,
        List<Integer> enchantOptions) {
    public static final int NO_MANA = -1;
    public static final int NO_TIME_LIMIT = -9999;
    public static final int NO_ATTACK_ELEMENT = -2;
    public static final int DEFENCE_ELEMENTS = 6;
    public static final int ENCHANT_OPTIONS = 3;

    static final int BYTES =
            (4 * 3) + 8 + (2 * 3) + 4 + (2 * 2) + (4 * 3) + (2 * 2) + (2 * DEFENCE_ELEMENTS) + (2 * ENCHANT_OPTIONS);
    /** A mail attachment repeats the object id after its block. */
    static final int ATTACHMENT_BYTES = BYTES + 4;

    public ItemBlock {
        elementDefence = List.copyOf(Objects.requireNonNull(elementDefence, "elementDefence"));
        if (elementDefence.size() != DEFENCE_ELEMENTS) {
            throw new IllegalArgumentException(
                    "An item has " + DEFENCE_ELEMENTS + " defence attributes, not " + elementDefence.size());
        }
        enchantOptions = List.copyOf(Objects.requireNonNull(enchantOptions, "enchantOptions"));
        if (enchantOptions.size() != ENCHANT_OPTIONS) {
            throw new IllegalArgumentException(
                    "An item has " + ENCHANT_OPTIONS + " enchant options, not " + enchantOptions.size());
        }
    }

    static ItemBlock read(ByteBuf in) {
        return ItemBlock.builder()
                .objectId(in.readIntLE())
                .displayId(in.readIntLE())
                .location(in.readIntLE())
                .count(in.readLongLE())
                .type2(in.readShortLE())
                .customType1(in.readShortLE())
                .equipped(in.readShortLE() != 0)
                .bodyPart(in.readIntLE())
                .enchantLevel(in.readShortLE())
                .customType2(in.readShortLE())
                .augmentationId(in.readIntLE())
                .mana(in.readIntLE())
                .remainingSeconds(in.readIntLE())
                .attackElementType(in.readShortLE())
                .attackElementPower(in.readShortLE())
                .elementDefence(readShorts(in, DEFENCE_ELEMENTS))
                .enchantOptions(readShorts(in, ENCHANT_OPTIONS))
                .build();
    }

    void write(ByteBuf out) {
        out.writeIntLE(objectId);
        out.writeIntLE(displayId);
        out.writeIntLE(location);
        out.writeLongLE(count);
        out.writeShortLE(type2);
        out.writeShortLE(customType1);
        out.writeShortLE(equipped ? 1 : 0);
        out.writeIntLE(bodyPart);
        out.writeShortLE(enchantLevel);
        out.writeShortLE(customType2);
        out.writeIntLE(augmentationId);
        out.writeIntLE(mana);
        out.writeIntLE(remainingSeconds);
        out.writeShortLE(attackElementType);
        out.writeShortLE(attackElementPower);
        for (int defence : elementDefence) {
            out.writeShortLE(defence);
        }
        for (int option : enchantOptions) {
            out.writeShortLE(option);
        }
    }

    static List<ItemBlock> readAttachments(ByteBuf in) {
        final int count = Bodies.count(in.readIntLE(), in, ATTACHMENT_BYTES);
        final List<ItemBlock> attachments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            attachments.add(read(in));
            in.skipBytes(4);
        }
        return attachments;
    }

    static void writeAttachments(ByteBuf out, List<ItemBlock> attachments) {
        out.writeIntLE(attachments.size());
        for (ItemBlock attachment : attachments) {
            attachment.write(out);
            out.writeIntLE(attachment.objectId());
        }
    }

    private static List<Integer> readShorts(ByteBuf in, int count) {
        final List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add((int) in.readShortLE());
        }
        return values;
    }
}
