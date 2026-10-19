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

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.mechanics.skill.Skill;
import lombok.Getter;

/**
 * Simple class for storing skill id/level.
 * @author BiggBoss
 */
public class SkillHolder {
    @Getter
    private final int skillId;

    @Getter
    private final int skillLevel;

    private Skill skill;

    public SkillHolder(int skillId, int skillLevel) {
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        skill = null;
    }

    public SkillHolder(Skill skill) {
        skillId = skill.getId();
        skillLevel = skill.getLevel();
        this.skill = skill;
    }

    public Skill getSkill() {
        if (skill == null) {
            skill = SkillData.getInstance().getSkill(skillId, Math.max(skillLevel, 1));
        }

        return skill;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SkillHolder)) {
            return false;
        }

        final SkillHolder holder = (SkillHolder) obj;
        return (holder.getSkillId() == skillId) && (holder.getSkillLevel() == skillLevel);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + skillId;
        result = (prime * result) + skillLevel;
        return result;
    }

    @Override
    public String toString() {
        return "[SkillId: " + skillId + " Level: " + skillLevel + "]";
    }
}
