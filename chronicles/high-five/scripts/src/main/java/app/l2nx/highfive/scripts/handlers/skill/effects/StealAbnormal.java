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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.util.StatSet;
import java.util.List;

/**
 * Steal Abnormal effect implementation.
 * @author Adry_85, Zoey76
 */
public class StealAbnormal extends AbstractEffect {
    private final String slot;
    private final int rate;
    private final int max;

    public StealAbnormal(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        slot = params.getString("slot", null);
        rate = params.getInt("rate", 0);
        max = params.getInt("max", 0);
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.STEAL_ABNORMAL;
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if ((effected != null) && effected.isPlayer() && (effector != effected)) {
            final List<BuffInfo> toSteal = Formulas.calcCancelStealEffects(effector, effected, skill, slot, rate, max);
            if (toSteal.isEmpty()) {
                return;
            }

            for (BuffInfo infoToSteal : toSteal) {
                // Invert effected and effector.
                final BuffInfo stolen = new BuffInfo(effected, effector, infoToSteal.getSkill());
                stolen.setAbnormalTime(infoToSteal.getTime()); // Copy the remaining time.

                // To include all the effects, it's required to go through the template rather the buff info.
                infoToSteal.getSkill().applyEffectScope(EffectScope.GENERAL, stolen, true, true);
                effected.getEffectList().remove(SkillFinishType.REMOVED, infoToSteal);
                effector.getEffectList().add(stolen);
            }
        }
    }
}
