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
package app.l2nx.highfive.scripts.ai.others.NpcBuffers;

import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.skill.targets.AffectObject;
import app.l2nx.highfive.mechanics.skill.targets.AffectScope;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class NpcBufferSkillData {
    private final SkillHolder skill;

    @Getter
    private final int initialDelay;

    @Getter
    private final int delay;

    @Getter
    private final AffectScope affectScope;

    @Getter
    private final AffectObject affectObject;

    public NpcBufferSkillData(StatSet set) {
        skill = new SkillHolder(set.getInt("id"), set.getInt("level"));
        initialDelay = set.getInt("skillInitDelay", 0) * 1000;
        delay = set.getInt("delay") * 1000;
        affectScope = set.getEnum("affectScope", AffectScope.class);
        affectObject = set.getEnum("affectObject", AffectObject.class);
    }

    public Skill getSkill() {
        return skill.getSkill();
    }
}
