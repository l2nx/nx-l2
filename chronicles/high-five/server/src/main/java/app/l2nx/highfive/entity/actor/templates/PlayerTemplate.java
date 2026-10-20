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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.util.StatSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * @author mkizub, Zoey76
 */
public class PlayerTemplate extends CreatureTemplate {
    @Getter
    private final PlayerClass playerClass;

    private final float[] baseHp;
    private final float[] baseMp;
    private final float[] baseCp;

    private final double[] baseHpReg;
    private final double[] baseMpReg;
    private final double[] baseCpReg;

    @Getter
    private final float fCollisionHeightFemale;

    @Getter
    private final float fCollisionRadiusFemale;

    @Getter
    private final int safeFallHeight;

    private final List<Location> creationPoints;
    private final Map<Integer, Integer> baseSlotDef;

    public PlayerTemplate(StatSet set, List<Location> creationPoints) {
        super(set);
        playerClass = PlayerClass.getPlayerClass(set.getInt("classId"));
        setRace(playerClass.getRace());
        baseHp = new float[ExperienceData.getInstance().getMaxLevel()];
        baseMp = new float[ExperienceData.getInstance().getMaxLevel()];
        baseCp = new float[ExperienceData.getInstance().getMaxLevel()];
        baseHpReg = new double[ExperienceData.getInstance().getMaxLevel()];
        baseMpReg = new double[ExperienceData.getInstance().getMaxLevel()];
        baseCpReg = new double[ExperienceData.getInstance().getMaxLevel()];
        baseSlotDef = new HashMap<>(12);
        baseSlotDef.put(Inventory.PAPERDOLL_CHEST, set.getInt("basePDefchest", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_LEGS, set.getInt("basePDeflegs", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_HEAD, set.getInt("basePDefhead", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_FEET, set.getInt("basePDeffeet", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_GLOVES, set.getInt("basePDefgloves", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_UNDER, set.getInt("basePDefunderwear", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_CLOAK, set.getInt("basePDefcloak", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_REAR, set.getInt("baseMDefrear", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_LEAR, set.getInt("baseMDeflear", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_RFINGER, set.getInt("baseMDefrfinger", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_LFINGER, set.getInt("baseMDefrfinger", 0));
        baseSlotDef.put(Inventory.PAPERDOLL_NECK, set.getInt("baseMDefneck", 0));

        fCollisionRadiusFemale = set.getFloat("collisionFemaleradius");
        fCollisionHeightFemale = set.getFloat("collisionFemaleheight");
        safeFallHeight = set.getInt("baseSafeFall", 333);
        this.creationPoints = creationPoints;
    }

    /**
     * @return random Location of created character spawn.
     */
    public Location getCreationPoint() {
        return creationPoints.get(ClockBridge.random().nextInt(creationPoints.size()));
    }

    /**
     * Sets the value of level upgain parameter.
     * @param paramName name of parameter
     * @param level corresponding character level
     * @param value value of parameter
     */
    public void setUpgainValue(String paramName, int level, double value) {
        switch (paramName) {
            case "hp": {
                baseHp[level] = (float) value;
                break;
            }
            case "mp": {
                baseMp[level] = (float) value;
                break;
            }
            case "cp": {
                baseCp[level] = (float) value;
                break;
            }
            case "hpRegen": {
                baseHpReg[level] = value;
                break;
            }
            case "mpRegen": {
                baseMpReg[level] = value;
                break;
            }
            case "cpRegen": {
                baseCpReg[level] = value;
                break;
            }
        }
    }

    /**
     * @param level character level to return value
     * @return the baseHpMax for given character level
     */
    public float getBaseHpMax(int level) {
        return baseHp[level];
    }

    /**
     * @param level character level to return value
     * @return the baseMpMax for given character level
     */
    public float getBaseMpMax(int level) {
        return baseMp[level];
    }

    /**
     * @param level character level to return value
     * @return the baseCpMax for given character level
     */
    public float getBaseCpMax(int level) {
        return baseCp[level];
    }

    /**
     * @param level character level to return value
     * @return the base HP Regeneration for given character level
     */
    public double getBaseHpRegen(int level) {
        return baseHpReg[level];
    }

    /**
     * @param level character level to return value
     * @return the base MP Regeneration for given character level
     */
    public double getBaseMpRegen(int level) {
        return baseMpReg[level];
    }

    /**
     * @param level character level to return value
     * @return the base HP Regeneration for given character level
     */
    public double getBaseCpRegen(int level) {
        return baseCpReg[level];
    }

    /**
     * @param slotId id of inventory slot to return value
     * @return defense value of character for EMPTY given slot
     */
    public int getBaseDefBySlot(int slotId) {
        return baseSlotDef.getOrDefault(slotId, 0);
    }
}
