/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.seed.model;

import app.l2nx.load.bot.model.Position;
import java.util.Locale;

/**
 * Where the seeded characters of an account stand. A character stands in one place and a template serves every
 * scenario, so each place has its own character: an account holds one per plan, in the slots they were created in.
 */
public enum CharacterPlan {
    /** A circle inside the zone {@code colosseum_battle1}, which is 1,480 units across at its narrowest. */
    ARENA(0, "%s%d", Position.builder().x(149_498).y(46_045).z(-3_400).build(), 650),

    /** Evil Hunting Grounds gatekeeper landing point: 28 ratmen of levels 18 to 20 within 2,500 units on open land. */
    HUNTING(1, "%sh%d", Position.builder().x(-6_989).y(109_503).z(-3_040).build(), 300);

    /** Under the reach of a melee attack, 40 units, so nobody has to run after a neighbour. */
    private static final int SPACING = 8;

    private final int slot;
    private final String nameFormat;
    private final Position center;
    private final int radius;

    CharacterPlan(int slot, String nameFormat, Position center, int radius) {
        this.slot = slot;
        this.nameFormat = nameFormat;
        this.center = center;
        this.radius = radius;
    }

    public int slot() {
        return slot;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String characterName(String accountPrefix, int index) {
        return nameFormat.formatted(accountPrefix, index);
    }

    /** Neighbours are consecutive indexes, so in the arena each stands beside the other team, within melee reach. */
    public Position position(int index, int bots) {
        final double radius = Math.min(this.radius, bots * SPACING / (2 * Math.PI));
        final double angle = 2 * Math.PI * index / bots;
        return center.toBuilder()
                .x(center.x() + (int) Math.round(radius * Math.cos(angle)))
                .y(center.y() + (int) Math.round(radius * Math.sin(angle)))
                .build();
    }
}
