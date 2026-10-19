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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import lombok.Getter;
import lombok.Setter;

/**
 * Character Sub-Class Definition<br>
 * Used to store key information about a character's sub-class.
 * @author Tempy
 */
public class SubClassHolder {
    private static final byte MAX_LEVEL =
            PlayerConfig.MAX_SUBCLASS_LEVEL < ExperienceData.getInstance().getMaxLevel()
                    ? PlayerConfig.MAX_SUBCLASS_LEVEL
                    : (byte) (ExperienceData.getInstance().getMaxLevel() - 1);

    @Getter
    private PlayerClass playerClass;

    @Getter
    private long exp = ExperienceData.getInstance().getExpForLevel(PlayerConfig.BASE_SUBCLASS_LEVEL);

    @Getter
    @Setter
    private long sp = 0;

    @Getter
    private byte level = PlayerConfig.BASE_SUBCLASS_LEVEL;

    @Getter
    @Setter
    private int classIndex = 1;

    public SubClassHolder(int classId, long exp, int sp, byte level, int classIndex) {
        playerClass = PlayerClass.getPlayerClass(classId);
        this.exp = exp;
        this.sp = sp;
        this.level = level;
        this.classIndex = classIndex;
    }

    public SubClassHolder(int classId, int classIndex) {
        // Used for defining a sub class using default values for XP, SP and player level.
        playerClass = PlayerClass.getPlayerClass(classId);
        this.classIndex = classIndex;
    }

    public SubClassHolder() {
        // Used for specifying ALL attributes of a sub class directly,
        // using the preset default values.
    }

    public int getId() {
        return playerClass.getId();
    }

    public void setPlayerClass(int id) {
        playerClass = PlayerClass.getPlayerClass(id);
    }

    public void setExp(long expValue) {
        if (expValue > (ExperienceData.getInstance().getExpForLevel(MAX_LEVEL + 1) - 1)) {
            exp = ExperienceData.getInstance().getExpForLevel(MAX_LEVEL + 1) - 1;
            return;
        }

        exp = expValue;
    }

    public void setLevel(byte levelValue) {
        if (levelValue > MAX_LEVEL) {
            level = MAX_LEVEL;
            return;
        } else if (levelValue < PlayerConfig.BASE_SUBCLASS_LEVEL) {
            level = PlayerConfig.BASE_SUBCLASS_LEVEL;
            return;
        }

        level = levelValue;
    }

    public void incLevel() {
        if (level == MAX_LEVEL) {
            return;
        }

        level++;
        setExp(ExperienceData.getInstance().getExpForLevel(getLevel()));
    }

    public void decLevel() {
        if (level == PlayerConfig.BASE_SUBCLASS_LEVEL) {
            return;
        }

        level--;
        setExp(ExperienceData.getInstance().getExpForLevel(getLevel()));
    }
}
