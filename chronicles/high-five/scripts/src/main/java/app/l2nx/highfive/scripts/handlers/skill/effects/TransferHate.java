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

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.util.StatSet;

/**
 * Transfer Hate effect implementation.
 * @author Adry_85
 */
public class TransferHate extends AbstractEffect {
    private final int chance;

    public TransferHate(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        chance = params.getInt("chance", 100);
    }

    @Override
    public boolean calcSuccess(Creature effector, Creature effected, Skill skill) {
        return Formulas.calcProbability(chance, effector, effected, skill);
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        World.forEachVisibleObjectInRange(effector, Attackable.class, skill.getAffectRange(), hater -> {
            if ((hater == null) || hater.isDead()) {
                return;
            }

            final long hate = hater.getHating(effector);
            if (hate <= 0) {
                return;
            }

            hater.reduceHate(effector, -hate);
            hater.addDamageHate(effected, 0, hate);
        });
    }
}
