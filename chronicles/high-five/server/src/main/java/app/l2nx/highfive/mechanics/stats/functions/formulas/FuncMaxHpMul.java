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
package app.l2nx.highfive.mechanics.stats.functions.formulas;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.BaseStat;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;

/**
 * @author UnAfraid
 */
public class FuncMaxHpMul extends AbstractFunction {
    private static final FuncMaxHpMul _fmhm_instance = new FuncMaxHpMul();

    public static AbstractFunction getInstance() {
        return _fmhm_instance;
    }

    private FuncMaxHpMul() {
        super(Stat.MAX_HP, 1, null, 0, null);
    }

    @Override
    public double calc(Creature effector, Creature effected, Skill skill, double initVal) {
        return initVal * BaseStat.CON.calcBonus(effector);
    }
}
