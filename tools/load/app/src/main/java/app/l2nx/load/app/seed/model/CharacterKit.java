/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.seed.model;

import lombok.Builder;

/**
 * What a seeded character is, on top of the starting gear of its base class, whose sword and wand stay equipped: no
 * client packet of the harness equips an item, so the kit's item is carried only.
 *
 * @param baseClassId the class created, which must be a starting class
 * @param skillId a single-target skill of the class at {@link #LEVEL}: the fighter's needs the starting sword
 */
@Builder(toBuilder = true)
public record CharacterKit(int race, int baseClassId, int classId, int skillId, int itemId, long itemCount) {
    public static final int LEVEL = 75;
    public static final int FEATHER_OF_BLESSING = 10649;

    public static final int FEATHERS = 1_000;

    private static final int HUMAN = 0;

    /** Gladiator with Fatal Strike, and no-grade soulshots. */
    private static final CharacterKit FIGHTER = CharacterKit.builder()
            .race(HUMAN)
            .baseClassId(0)
            .classId(2)
            .skillId(190)
            .itemId(1835)
            .itemCount(1_000)
            .build();

    /** Sorcerer with Prominence, and no-grade spiritshots. */
    private static final CharacterKit MAGE = CharacterKit.builder()
            .race(HUMAN)
            .baseClassId(10)
            .classId(12)
            .skillId(1230)
            .itemId(2509)
            .itemCount(1_000)
            .build();

    /** In pairs of indexes, as teams go by parity: each team gets both kits. */
    public static CharacterKit of(int index) {
        return (index / 2) % 2 == 0 ? FIGHTER : MAGE;
    }
}
