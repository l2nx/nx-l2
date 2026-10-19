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
package app.l2nx.highfive.mechanics.stats.functions;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import lombok.Getter;

/**
 * A Function object is a component of a Calculator created to manage and dynamically calculate the effect of a character property (ex : MAX_HP, REGENERATE_HP_RATE...).<br>
 * In fact, each calculator is a table of functions object in which each function represents a mathematics function:<br>
 * FuncAtkAccuracy -> Math.sqrt(_player.getDEX())*6+_player.getLevel()<br>
 * When the calc method of a calculator is launched, each mathematics function is called according to its priority <b>order</b>.<br>
 * Indeed, functions with lowest priority order is executed first and functions with the same order are executed in unspecified order.<br>
 * @author Zoey76
 */
public abstract class AbstractFunction {
    /** Statistics, that is affected by this function (See Creature.CALCULATOR_XXX constants) */
    @Getter
    private final Stat stat;
    /**
     * Order of functions calculation.<br>
     * Functions with lower order are executed first.<br>
     * Functions with the same order are executed in unspecified order.<br>
     * Usually add/subtract functions has lowest order,<br>
     * then bonus/penalty functions (multiply/divide) are applied, then functions that do more complex<br>
     * calculations (non-linear functions).
     */
    @Getter
    private final int order;
    /**
     * Owner can be an armor, weapon, skill, system event, quest, etc.<br>
     * Used to remove all functions added by this owner.
     */
    @Getter
    private final Object funcOwner;
    /** Function may be disabled by attached condition. */
    @Getter
    private final Condition applyCond;
    /** The value. */
    @Getter
    private final double value;

    /**
     * Constructor of Func.
     * @param stat the stat
     * @param order the order
     * @param owner the owner
     * @param value the value
     * @param applyCond the apply condition
     */
    public AbstractFunction(Stat stat, int order, Object owner, double value, Condition applyCond) {
        this.stat = stat;
        this.order = order;
        funcOwner = owner;
        this.value = value;
        this.applyCond = applyCond;
    }

    /**
     * Run the mathematics function of the Func.
     * @param effector the effector
     * @param effected the effected
     * @param skill the skill
     * @param initVal the initial value
     * @return the calculated value
     */
    public abstract double calc(Creature effector, Creature effected, Skill skill, double initVal);
}
