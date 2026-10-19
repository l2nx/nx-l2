/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.mechanics.stats.MoveType;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * Stats definition for each pet level.
 * @author JIV
 */
public class PetLevelData {
    @Getter
    private final int ownerExpTaken;

    @Getter
    private final int petFeedBattle;

    @Getter
    private final int petFeedNormal;

    @Getter
    private final float petMAtk;

    @Getter
    private final long petMaxExp;

    @Getter
    private final int petMaxFeed;

    @Getter
    private final float petMaxHP;

    @Getter
    private final float petMaxMP;

    @Getter
    private final float petMDef;

    @Getter
    private final float petPAtk;

    @Getter
    private final float petPDef;

    @Getter
    private final float petRegenHP;

    @Getter
    private final float petRegenMP;

    @Getter
    private final short petSoulShot;

    @Getter
    private final short petSpiritShot;

    private final double walkSpeedOnRide;
    private final double runSpeedOnRide;
    private final double slowSwimSpeedOnRide;
    private final double fastSwimSpeedOnRide;
    private final double slowFlySpeedOnRide;
    private final double fastFlySpeedOnRide;

    public PetLevelData(StatSet set) {
        ownerExpTaken = set.getInt("get_exp_type");
        petMaxExp = (long) set.getDouble("exp");
        petMaxHP = set.getFloat("org_hp");
        petMaxMP = set.getFloat("org_mp");
        petPAtk = set.getFloat("org_pattack");
        petPDef = set.getFloat("org_pdefend");
        petMAtk = set.getFloat("org_mattack");
        petMDef = set.getFloat("org_mdefend");
        petMaxFeed = set.getInt("max_meal");
        petFeedBattle = set.getInt("consume_meal_in_battle");
        petFeedNormal = set.getInt("consume_meal_in_normal");
        petRegenHP = set.getFloat("org_hp_regen");
        petRegenMP = set.getFloat("org_mp_regen");
        petSoulShot = set.getShort("soulshot_count");
        petSpiritShot = set.getShort("spiritshot_count");
        walkSpeedOnRide = set.getDouble("walkSpeedOnRide", 0);
        runSpeedOnRide = set.getDouble("runSpeedOnRide", 0);
        slowSwimSpeedOnRide = set.getDouble("slowSwimSpeedOnRide", 0);
        fastSwimSpeedOnRide = set.getDouble("fastSwimSpeedOnRide", 0);
        slowFlySpeedOnRide = set.getDouble("slowFlySpeedOnRide", 0);
        fastFlySpeedOnRide = set.getDouble("fastFlySpeedOnRide", 0);
    }

    /**
     * @param mt movement type
     * @return the base riding speed of given movement type.
     */
    public double getSpeedOnRide(MoveType mt) {
        switch (mt) {
            case WALK: {
                return walkSpeedOnRide;
            }
            case RUN: {
                return runSpeedOnRide;
            }
            case SLOW_SWIM: {
                return slowSwimSpeedOnRide;
            }
            case FAST_SWIM: {
                return fastSwimSpeedOnRide;
            }
            case SLOW_FLY: {
                return slowFlySpeedOnRide;
            }
            case FAST_FLY: {
                return fastFlySpeedOnRide;
            }
        }

        return 0;
    }
}
