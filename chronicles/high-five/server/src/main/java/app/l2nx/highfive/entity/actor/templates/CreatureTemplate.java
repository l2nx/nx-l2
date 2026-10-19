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
package app.l2nx.highfive.entity.actor.templates;

import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.MoveType;
import app.l2nx.highfive.util.StatSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Character template.
 * @author Zoey76
 */
public class CreatureTemplate extends ListenersContainer {
    // BaseStats
    @Getter
    private int baseSTR;

    @Getter
    private int baseCON;

    @Getter
    private int baseDEX;

    @Getter
    private int baseINT;

    @Getter
    private int baseWIT;

    @Getter
    private int baseMEN;

    @Getter
    protected float baseHpMax;

    @Getter
    private float baseCpMax;

    @Getter
    protected float baseMpMax;

    @Getter
    private float baseHpReg;

    @Getter
    private float baseMpReg;

    @Getter
    protected int basePAtk;

    @Getter
    protected int baseMAtk;

    @Getter
    protected int basePDef;

    @Getter
    protected int baseMDef;

    @Getter
    private int basePAtkSpd;

    @Getter
    private int baseMAtkSpd;

    @Getter
    @Setter
    private int baseAttackRange;

    @Getter
    private int randomDamage;

    @Getter
    @Setter
    private WeaponType baseAttackType;

    @Getter
    private int baseShldDef;

    @Getter
    private int baseShldRate;

    @Getter
    private int baseCritRate;

    @Getter
    private int baseMCritRate;

    // SpecialStats
    @Getter
    private int baseBreath;

    @Getter
    @Setter
    private int baseFire;

    @Getter
    @Setter
    private int baseWind;

    @Getter
    @Setter
    private int baseWater;

    @Getter
    @Setter
    private int baseEarth;

    @Getter
    @Setter
    private int baseHoly;

    @Getter
    @Setter
    private int baseDark;

    @Getter
    @Setter
    private double baseFireRes;

    @Getter
    @Setter
    private double baseWindRes;

    @Getter
    @Setter
    private double baseWaterRes;

    @Getter
    @Setter
    private double baseEarthRes;

    @Getter
    @Setter
    private double baseHolyRes;

    @Getter
    @Setter
    private double baseDarkRes;

    @Getter
    @Setter
    private double baseElementRes;
    /** For client info use {@link #fCollisionRadius} */
    @Getter
    private int collisionRadius;
    /** For client info use {@link #fCollisionHeight} */
    @Getter
    private int collisionHeight;

    @Getter
    private float fCollisionRadius;

    @Getter
    private float fCollisionHeight;

    private final double[] moveType = new double[MoveType.values().length];
    /** The creature's race. */
    @Getter
    @Setter
    private Race race;

    public CreatureTemplate(StatSet set) {
        set(set);
    }

    public void set(StatSet set) {
        // Base stats
        baseSTR = set.getInt("baseSTR", 0);
        baseCON = set.getInt("baseCON", 0);
        baseDEX = set.getInt("baseDEX", 0);
        baseINT = set.getInt("baseINT", 0);
        baseWIT = set.getInt("baseWIT", 0);
        baseMEN = set.getInt("baseMEN", 0);
        baseHpMax = set.getFloat("baseHpMax", 0);
        baseCpMax = set.getFloat("baseCpMax", 0);
        baseMpMax = set.getFloat("baseMpMax", 0);
        baseHpReg = set.getFloat("baseHpReg", 0);
        baseMpReg = set.getFloat("baseMpReg", 0);
        basePAtk = set.getInt("basePAtk", 0);
        baseMAtk = set.getInt("baseMAtk", 0);
        basePDef = set.getInt("basePDef", 0);
        baseMDef = set.getInt("baseMDef", 0);
        basePAtkSpd = set.getInt("basePAtkSpd", 300);
        baseMAtkSpd = set.getInt("baseMAtkSpd", 333);
        baseShldDef = set.getInt("baseShldDef", 0);
        baseAttackRange = set.getInt("baseAtkRange", 40);
        randomDamage = set.getInt("baseRndDam", 0);
        baseAttackType = set.getEnum("baseAtkType", WeaponType.class, WeaponType.FIST);
        baseShldRate = set.getInt("baseShldRate", 0);
        baseCritRate = set.getInt("baseCritRate", 4);
        baseMCritRate = set.getInt("baseMCritRate", 5);

        // SpecialStats
        baseBreath = set.getInt("baseBreath", 100);
        baseFire = set.getInt("baseFire", 0);
        baseWind = set.getInt("baseWind", 0);
        baseWater = set.getInt("baseWater", 0);
        baseEarth = set.getInt("baseEarth", 0);
        baseHoly = set.getInt("baseHoly", 0);
        baseDark = set.getInt("baseDark", 0);
        baseFireRes = set.getInt("baseFireRes", 0);
        baseWindRes = set.getInt("baseWindRes", 0);
        baseWaterRes = set.getInt("baseWaterRes", 0);
        baseEarthRes = set.getInt("baseEarthRes", 0);
        baseHolyRes = set.getInt("baseHolyRes", 0);
        baseDarkRes = set.getInt("baseDarkRes", 0);
        baseElementRes = set.getInt("baseElementRes", 0);

        // Geometry
        fCollisionHeight = set.getFloat("collisionHeight", 0);
        fCollisionRadius = set.getFloat("collisionRadius", 0);
        collisionRadius = (int) fCollisionRadius;
        collisionHeight = (int) fCollisionHeight;

        // speed.
        Arrays.fill(moveType, 1);
        setBaseMoveSpeed(MoveType.RUN, set.getDouble("baseRunSpd", 120));
        setBaseMoveSpeed(MoveType.WALK, set.getDouble("baseWalkSpd", 50));
        setBaseMoveSpeed(MoveType.FAST_SWIM, set.getDouble("baseSwimRunSpd", getBaseMoveSpeed(MoveType.RUN)));
        setBaseMoveSpeed(MoveType.SLOW_SWIM, set.getDouble("baseSwimWalkSpd", getBaseMoveSpeed(MoveType.WALK)));
        setBaseMoveSpeed(MoveType.FAST_FLY, set.getDouble("baseFlyRunSpd", getBaseMoveSpeed(MoveType.RUN)));
        setBaseMoveSpeed(MoveType.SLOW_FLY, set.getDouble("baseFlyWalkSpd", getBaseMoveSpeed(MoveType.WALK)));
    }

    public void setBaseMoveSpeed(MoveType type, double value) {
        moveType[type.ordinal()] = value;
    }

    public double getBaseMoveSpeed(MoveType mt) {
        return moveType[mt.ordinal()];
    }

    /**
     * Overridden in NpcTemplate
     * @return the characters skills
     */
    public Map<Integer, Skill> getSkills() {
        return Collections.emptyMap();
    }
}
