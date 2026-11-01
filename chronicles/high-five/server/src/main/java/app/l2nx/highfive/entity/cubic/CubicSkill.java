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
package app.l2nx.highfive.entity.cubic;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.cubic.conditions.ICubicCondition;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * Cubic skill data loaded from XML.<br>
 * Stores target routing and optional execution conditions for a cubic skill.
 * @author UnAfraid
 */
public class CubicSkill extends SkillHolder implements ICubicConditionHolder {
    @Getter
    private final int triggerRate;

    @Getter
    private final CubicTargetType targetType;

    private final boolean targetDebuff;

    @Getter
    private final boolean priority;

    private final List<ICubicCondition> conditions = new ArrayList<>();

    public CubicSkill(StatSet set) {
        super(set.getInt("id"), set.getInt("level"));
        triggerRate = set.getInt("triggerRate", 100);
        targetType = set.getEnum("target", CubicTargetType.class, CubicTargetType.TARGET);
        targetDebuff = set.getBoolean("targetDebuff", false);
        priority = set.getBoolean("priority", false);
    }

    @Override
    public boolean validateConditions(Cubic cubic, Creature owner, WorldObject target) {
        if (target == null) {
            return false;
        }

        if (targetDebuff && !hasDispellableDebuff(target)) {
            return false;
        }

        if (conditions.isEmpty()) {
            return true;
        }

        for (ICubicCondition condition : conditions) {
            if (!condition.test(cubic, owner, target)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasDispellableDebuff(WorldObject target) {
        if (!target.isCreature()) {
            return false;
        }

        return target.asCreature().getEffectList().hasDispellableDebuff();
    }

    @Override
    public void addCondition(ICubicCondition condition) {
        conditions.add(condition);
    }
}
