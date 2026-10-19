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
package app.l2nx.highfive.entity.actor.transform;

import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.stats.MoveType;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.serverpackets.ExBasicActionList;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * @author UnAfraid
 */
public class TransformTemplate {
    @Getter
    private final float collisionRadius;

    @Getter
    private final float collisionHeight;

    @Getter
    private final WeaponType baseAttackType;

    @Getter
    private final int baseAttackRange;

    @Getter
    private final double baseRandomDamage;

    private List<SkillHolder> skills;
    private List<AdditionalSkillHolder> additionalSkills;
    private List<AdditionalItemHolder> additionalItems;
    private Map<Integer, Integer> baseDefense;
    private Map<Integer, Double> baseStats;
    private Map<Integer, Double> baseSpeed;

    @Setter
    private ExBasicActionList basicActionList;

    private final Map<Integer, TransformLevelData> data = new LinkedHashMap<>(100);

    public TransformTemplate(StatSet set) {
        collisionRadius = set.getFloat("radius", 0);
        collisionHeight = set.getFloat("height", 0);
        baseAttackType = set.getEnum("attackType", WeaponType.class, WeaponType.FIST);
        baseAttackRange = set.getInt("range", 40);
        baseRandomDamage = set.getDouble("randomDamage", 0);

        addSpeed(MoveType.WALK, set.getDouble("walk", 0));
        addSpeed(MoveType.RUN, set.getDouble("run", 0));
        addSpeed(MoveType.SLOW_SWIM, set.getDouble("waterWalk", 0));
        addSpeed(MoveType.FAST_SWIM, set.getDouble("waterRun", 0));
        addSpeed(MoveType.SLOW_FLY, set.getDouble("flyWalk", 0));
        addSpeed(MoveType.FAST_FLY, set.getDouble("flyRun", 0));

        addStats(Stat.POWER_ATTACK, set.getDouble("pAtk", 0));
        addStats(Stat.MAGIC_ATTACK, set.getDouble("mAtk", 0));
        addStats(Stat.POWER_ATTACK_RANGE, set.getInt("range", 0));
        addStats(Stat.POWER_ATTACK_SPEED, set.getInt("attackSpeed", 0));
        addStats(Stat.CRITICAL_RATE, set.getInt("critRate", 0));
        addStats(Stat.STAT_STR, set.getInt("str", 0));
        addStats(Stat.STAT_INT, set.getInt("int", 0));
        addStats(Stat.STAT_CON, set.getInt("con", 0));
        addStats(Stat.STAT_DEX, set.getInt("dex", 0));
        addStats(Stat.STAT_WIT, set.getInt("wit", 0));
        addStats(Stat.STAT_MEN, set.getInt("men", 0));

        addDefense(Inventory.PAPERDOLL_CHEST, set.getInt("chest", 0));
        addDefense(Inventory.PAPERDOLL_LEGS, set.getInt("legs", 0));
        addDefense(Inventory.PAPERDOLL_HEAD, set.getInt("head", 0));
        addDefense(Inventory.PAPERDOLL_FEET, set.getInt("feet", 0));
        addDefense(Inventory.PAPERDOLL_GLOVES, set.getInt("gloves", 0));
        addDefense(Inventory.PAPERDOLL_UNDER, set.getInt("underwear", 0));
        addDefense(Inventory.PAPERDOLL_CLOAK, set.getInt("cloak", 0));
        addDefense(Inventory.PAPERDOLL_REAR, set.getInt("rear", 0));
        addDefense(Inventory.PAPERDOLL_LEAR, set.getInt("lear", 0));
        addDefense(Inventory.PAPERDOLL_RFINGER, set.getInt("rfinger", 0));
        addDefense(Inventory.PAPERDOLL_LFINGER, set.getInt("lfinger", 0));
        addDefense(Inventory.PAPERDOLL_NECK, set.getInt("neck", 0));
    }

    private void addSpeed(MoveType type, double value) {
        if (baseSpeed == null) {
            baseSpeed = new HashMap<>();
        }

        baseSpeed.put(type.ordinal(), value);
    }

    public double getBaseMoveSpeed(MoveType type) {
        if ((baseSpeed == null) || !baseSpeed.containsKey(type.ordinal())) {
            return 0;
        }

        return baseSpeed.get(type.ordinal());
    }

    private void addDefense(int type, int value) {
        if (baseDefense == null) {
            baseDefense = new HashMap<>();
        }

        baseDefense.put(type, value);
    }

    public int getDefense(int type) {
        if ((baseDefense == null) || !baseDefense.containsKey(type)) {
            return 0;
        }

        return baseDefense.get(type);
    }

    private void addStats(Stat stat, double value) {
        if (baseStats == null) {
            baseStats = new HashMap<>();
        }

        baseStats.put(stat.ordinal(), value);
    }

    public double getStats(Stat stat) {
        if ((baseStats == null) || !baseStats.containsKey(stat.ordinal())) {
            return 0;
        }

        return baseStats.get(stat.ordinal());
    }

    public void addSkill(SkillHolder holder) {
        if (skills == null) {
            skills = new ArrayList<>();
        }

        skills.add(holder);
    }

    public List<SkillHolder> getSkills() {
        return skills != null ? skills : Collections.<SkillHolder>emptyList();
    }

    public void addAdditionalSkill(AdditionalSkillHolder holder) {
        if (additionalSkills == null) {
            additionalSkills = new ArrayList<>();
        }

        additionalSkills.add(holder);
    }

    public List<AdditionalSkillHolder> getAdditionalSkills() {
        return additionalSkills != null ? additionalSkills : Collections.<AdditionalSkillHolder>emptyList();
    }

    public void addAdditionalItem(AdditionalItemHolder holder) {
        if (additionalItems == null) {
            additionalItems = new ArrayList<>();
        }

        additionalItems.add(holder);
    }

    public List<AdditionalItemHolder> getAdditionalItems() {
        return additionalItems != null ? additionalItems : Collections.<AdditionalItemHolder>emptyList();
    }

    public ExBasicActionList getBasicActionList() {
        return basicActionList;
    }

    public boolean hasBasicActionList() {
        return basicActionList != null;
    }

    public void addLevelData(TransformLevelData data) {
        this.data.put(data.getLevel(), data);
    }

    public TransformLevelData getData(int level) {
        return data.get(level);
    }
}
