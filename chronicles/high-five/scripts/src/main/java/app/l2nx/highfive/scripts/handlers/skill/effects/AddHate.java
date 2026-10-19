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
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Add Hate effect implementation.
 * @author Adry_85
 */
public class AddHate extends AbstractEffect {
    private final double _power;

    public AddHate(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        _power = params.getDouble("power", 0);
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (!effected.isAttackable()) {
            return;
        }

        final double val = _power;
        if (val > 0) {
            effected.asAttackable().addDamageHate(effector, 0, (int) val);
        } else if (val < 0) {
            effected.asAttackable().reduceHate(effector, (int) -val);
        }
    }
}
