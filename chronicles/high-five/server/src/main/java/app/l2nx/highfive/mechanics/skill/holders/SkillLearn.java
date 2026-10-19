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
package app.l2nx.highfive.mechanics.skill.holders;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.enums.player.SocialClass;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * @author Zoey76
 */
public class SkillLearn {
    @Getter
    private final String name;

    @Getter
    private final int skillId;

    @Getter
    private final int skillLevel;

    @Getter
    private final int getLevel;

    @Getter
    private final boolean autoGet;

    @Getter
    private final int levelUpSp;

    @Getter
    private final Set<ItemHolder> requiredItems = new HashSet<>(1);

    @Getter
    private final Set<Race> races = EnumSet.noneOf(Race.class);

    @Getter
    private final Set<SkillHolder> preReqSkills = new HashSet<>(1);

    @Getter
    private SocialClass socialClass;

    @Getter
    private final boolean residencialSkill;

    @Getter
    private final Set<Integer> residenceIds = new HashSet<>(1);

    @Getter
    private final List<SubClassData> subClassConditions = new ArrayList<>(1);

    @Getter
    private final boolean learnedByNpc;

    @Getter
    private final boolean learnedByFS;

    public class SubClassData {
        @Getter
        private final int slot;

        private final int level;

        public SubClassData(int pSlot, int pLvl) {
            slot = pSlot;
            level = pLvl;
        }

        /**
         * @return the required sub-class level.
         */
        public int getLvl() {
            return level;
        }
    }

    /**
     * Constructor for SkillLearn.
     * @param set the set with the SkillLearn data.
     */
    public SkillLearn(StatSet set) {
        name = set.getString("skillName");
        skillId = set.getInt("skillId");
        skillLevel = set.getInt("skillLevel");
        getLevel = set.getInt("getLevel");
        autoGet = set.getBoolean("autoGet", false);
        levelUpSp = set.getInt("levelUpSp", 0);
        residencialSkill = set.getBoolean("residenceSkill", false);
        learnedByNpc = set.getBoolean("learnedByNpc", false);
        learnedByFS = set.getBoolean("learnedByFS", false);
    }

    /**
     * Adds a required item holder to learn this skill.
     * @param item the required item holder.
     */
    public void addRequiredItem(ItemHolder item) {
        requiredItems.add(item);
    }

    /**
     * Adds a required race to learn this skill.
     * @param race the required race.
     */
    public void addRace(Race race) {
        races.add(race);
    }

    /**
     * Adds a required skill holder to learn this skill.
     * @param skill the required skill holder.
     */
    public void addPreReqSkill(SkillHolder skill) {
        preReqSkills.add(skill);
    }

    /**
     * Sets the social class if hasn't been set before.
     * @param socialClass the social class to set.
     */
    public void setSocialClass(SocialClass socialClass) {
        if (this.socialClass == null) {
            this.socialClass = socialClass;
        }
    }

    /**
     * Adds a required residence Id.
     * @param id the residence Id to add.
     */
    public void addResidenceId(Integer id) {
        residenceIds.add(id);
    }

    /**
     * Adds a required residence Id.
     * @param slot the sub-class slot.
     * @param level the required sub-class level.
     */
    public void addSubclassConditions(int slot, int level) {
        subClassConditions.add(new SubClassData(slot, level));
    }

    /**
     * Used for AltGameSkillLearn mod.<br>
     * If the alternative skill learn system is enabled and the player is learning a skill from a different class apply a fee.<br>
     * If the player is learning a skill from other class type (mage learning warrior skills or vice versa) the fee is higher.
     * @param playerClass the player class Id.
     * @param learningClass the skill learning player class Id.
     * @return the amount of SP required to acquire this skill, by calculating the cost for the alternative skill learn system.
     */
    public int getCalculatedLevelUpSp(PlayerClass playerClass, PlayerClass learningClass) {
        if ((playerClass == null) || (learningClass == null)) {
            return this.levelUpSp;
        }

        int levelUpSp = this.levelUpSp;

        // If the alternative skill learn system is enabled and the player is learning a skill from a different class
        // apply a fee.
        if (PlayerConfig.ALT_GAME_SKILL_LEARN && (playerClass != learningClass)) {
            // If the player is learning a skill from other class type (mage learning warrior skills or vice versa) the
            // fee is higher.
            if (playerClass.isMage() != learningClass.isMage()) {
                levelUpSp *= 3;
            } else {
                levelUpSp *= 2;
            }
        }

        return levelUpSp;
    }
}
