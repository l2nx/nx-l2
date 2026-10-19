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
package app.l2nx.highfive.mechanics.conditions;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;

/**
 * Condition implementation to verify player's abnormal type and level.
 * @author Zoey76
 */
public class ConditionPlayerCheckAbnormal extends Condition {
    private final AbnormalType type;
    private final int level;

    /**
     * Instantiates a new condition player check abnormal.
     * @param type the abnormal type
     */
    public ConditionPlayerCheckAbnormal(AbnormalType type) {
        this.type = type;
        level = -1;
    }

    /**
     * Instantiates a new condition player check abnormal.
     * @param type the abnormal type
     * @param level the abnormal level
     */
    public ConditionPlayerCheckAbnormal(AbnormalType type, int level) {
        this.type = type;
        this.level = level;
    }

    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        final BuffInfo info = effector.getEffectList().getBuffInfoByAbnormalType(type);
        return (info != null) && ((level == -1) || (level >= info.getSkill().getAbnormalLevel()));
    }
}
