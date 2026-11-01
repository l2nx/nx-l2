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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * Character Sub-Class Definition<br>
 * Used to store key information about a character's sub-class.
 * @author Tempy
 */
@SingleWriter
public class SubClassHolder {
    private PlayerClass playerClass;
    private long exp;
    private long sp;
    private byte level = PlayerConfig.BASE_SUBCLASS_LEVEL;
    private int classIndex = 1;

    public SubClassHolder(int classId, long exp, int sp, byte level, int classIndex) {
        playerClass = PlayerClass.getPlayerClass(classId);
        this.exp = exp;
        this.sp = sp;
        this.level = level;
        this.classIndex = classIndex;
    }

    public SubClassHolder(int classId, int classIndex) {
        playerClass = PlayerClass.getPlayerClass(classId);
        exp = ExperienceData.getInstance().getExpForLevel(PlayerConfig.BASE_SUBCLASS_LEVEL);
        this.classIndex = classIndex;
    }

    public SubClassHolder() {
        exp = ExperienceData.getInstance().getExpForLevel(PlayerConfig.BASE_SUBCLASS_LEVEL);
    }

    // Read from the experience table on use, not at class load, so the class loads before the table does.
    private static byte maxLevel() {
        final byte tableMax = ExperienceData.getInstance().getMaxLevel();
        return PlayerConfig.MAX_SUBCLASS_LEVEL < tableMax ? PlayerConfig.MAX_SUBCLASS_LEVEL : (byte) (tableMax - 1);
    }

    public int getId() {
        WorldPartition.checkOwner();
        return playerClass.getId();
    }

    public PlayerClass getPlayerClass() {
        WorldPartition.checkOwner();
        return playerClass;
    }

    public void setPlayerClass(int id) {
        WorldPartition.checkOwner();
        playerClass = PlayerClass.getPlayerClass(id);
    }

    public long getExp() {
        WorldPartition.checkOwner();
        return exp;
    }

    public void setExp(long expValue) {
        WorldPartition.checkOwner();
        final long expCap = ExperienceData.getInstance().getExpForLevel(maxLevel() + 1) - 1;
        exp = Math.min(expValue, expCap);
    }

    public long getSp() {
        WorldPartition.checkOwner();
        return sp;
    }

    public void setSp(long sp) {
        WorldPartition.checkOwner();
        this.sp = sp;
    }

    public byte getLevel() {
        WorldPartition.checkOwner();
        return level;
    }

    public void setLevel(byte levelValue) {
        WorldPartition.checkOwner();
        final byte maxLevel = maxLevel();
        if (levelValue > maxLevel) {
            level = maxLevel;
        } else if (levelValue < PlayerConfig.BASE_SUBCLASS_LEVEL) {
            level = PlayerConfig.BASE_SUBCLASS_LEVEL;
        } else {
            level = levelValue;
        }
    }

    public int getClassIndex() {
        WorldPartition.checkOwner();
        return classIndex;
    }

    public void setClassIndex(int classIndex) {
        WorldPartition.checkOwner();
        this.classIndex = classIndex;
    }

    public void incLevel() {
        WorldPartition.checkOwner();
        if (level == maxLevel()) {
            return;
        }

        level++;
        setExp(ExperienceData.getInstance().getExpForLevel(level));
    }

    public void decLevel() {
        WorldPartition.checkOwner();
        if (level == PlayerConfig.BASE_SUBCLASS_LEVEL) {
            return;
        }

        level--;
        setExp(ExperienceData.getInstance().getExpForLevel(level));
    }
}
