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

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Class hold information about basic pet stats which are same on each level.
 * @author JIV
 */
public class PetData {
    private final Map<Integer, PetLevelData> levelStats = new HashMap<>();

    @Getter
    private final List<PetSkillLearn> availableSkills = new ArrayList<>();

    @Getter
    private final int npcId;

    @Getter
    private final int itemId;

    @Getter
    @Setter
    private int load = 20000;

    @Getter
    @Setter
    private int hungryLimit = 1;

    @Getter
    private int minLevel = Byte.MAX_VALUE;

    @Getter
    private int maxLevel = 0;

    @Getter
    private boolean synchLevel = false;

    @Getter
    private final Set<Integer> food = new HashSet<>();

    public PetData(int npcId, int itemId) {
        this.npcId = npcId;
        this.itemId = itemId;
    }

    /**
     * @param level the pet's level.
     * @param data the pet's data.
     */
    public void addNewStat(int level, PetLevelData data) {
        if (minLevel > level) {
            minLevel = level;
        }

        if (maxLevel < level) {
            maxLevel = level;
        }

        levelStats.put(level, data);
    }

    /**
     * @param petLevel the pet's level.
     * @return the pet data associated to that pet level.
     */
    public PetLevelData getPetLevelData(int petLevel) {
        return levelStats.get(petLevel);
    }

    /**
     * @param foodId the pet's food Id to add.
     */
    public void addFood(Integer foodId) {
        food.add(foodId);
    }

    /**
     * @param value synchronizes level with master or not.
     */
    public void setSyncLevel(boolean value) {
        synchLevel = value;
    }

    // SKILS

    /**
     * @param skillId the skill Id to add.
     * @param skillLevel the skill level.
     * @param petLvl the pet's level when this skill is available.
     */
    public void addNewSkill(int skillId, int skillLevel, int petLvl) {
        availableSkills.add(new PetSkillLearn(skillId, skillLevel, petLvl));
    }

    /**
     * @param skillId the skill Id.
     * @param petLvl the pet level.
     * @return the level of the skill for the given skill Id and pet level.
     */
    public int getAvailableLevel(int skillId, int petLvl) {
        int lvl = 0;
        boolean found = false;
        for (PetSkillLearn temp : availableSkills) {
            if (temp.getSkillId() != skillId) {
                continue;
            }

            found = true;
            if (temp.getSkillLevel() == 0) {
                if (petLvl < 70) {
                    lvl = (petLvl / 10);
                    if (lvl <= 0) {
                        lvl = 1;
                    }
                } else {
                    lvl = (7 + ((petLvl - 70) / 5));
                }

                // Formula usable for skill that have 10 or more skill levels.
                final int maxLevel = SkillData.getInstance().getMaxLevel(temp.getSkillId());
                if (lvl > maxLevel) {
                    lvl = maxLevel;
                }
                break;
            } else if ((temp.getMinLevel() <= petLvl) && (temp.getSkillLevel() > lvl)) {
                lvl = temp.getSkillLevel();
            }
        }

        if (found && (lvl == 0)) {
            return 1;
        }

        return lvl;
    }

    public static class PetSkillLearn extends SkillHolder {
        @Getter
        private final int minLevel;

        /**
         * @param id the skill Id.
         * @param lvl the skill level.
         * @param minLevel the minimum level when this skill is available.
         */
        public PetSkillLearn(int id, int lvl, int minLevel) {
            super(id, lvl);
            this.minLevel = minLevel;
        }
    }
}
