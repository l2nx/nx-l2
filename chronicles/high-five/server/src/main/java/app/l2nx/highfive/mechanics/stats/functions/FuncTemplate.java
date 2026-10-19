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
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import java.lang.reflect.Constructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Function template.
 * @author mkizub, Zoey76
 */
@Slf4j
public class FuncTemplate {

    private final Condition attachCond;
    private final Condition applyCond;
    private final Constructor<?> constructor;

    @Getter
    private final Stat stat;

    @Getter
    private final int order;

    @Getter
    private final double value;

    public FuncTemplate(
            Condition attachCond, Condition applyCond, String functionName, int order, Stat stat, double value) {
        final StatFunction function = StatFunction.valueOf(functionName.toUpperCase());
        if (order >= 0) {
            this.order = order;
        } else {
            this.order = function.getOrder();
        }

        this.attachCond = attachCond;
        this.applyCond = applyCond;
        this.stat = stat;
        this.value = value;

        try {
            final Class<?> functionClass =
                    Class.forName("app.l2nx.highfive.mechanics.stats.functions.Func" + function.getName());
            constructor = functionClass.getConstructor(
                    Stat.class, // Stats to update
                    Integer.TYPE, // Order of execution
                    Object.class, // Owner
                    Double.TYPE, // Value for function
                    Condition.class // Condition
                    );
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the functions for skills.
     * @param caster the caster
     * @param target the target
     * @param skill the skill
     * @param owner the owner
     * @return the function if conditions are met, {@code null} otherwise
     */
    public AbstractFunction getFunc(Creature caster, Creature target, Skill skill, Object owner) {
        return getFunc(caster, target, skill, null, owner);
    }

    /**
     * Gets the functions for items.
     * @param caster the caster
     * @param target the target
     * @param item the item
     * @param owner the owner
     * @return the function if conditions are met, {@code null} otherwise
     */
    public AbstractFunction getFunc(Creature caster, Creature target, Item item, Object owner) {
        return getFunc(caster, target, null, item, owner);
    }

    /**
     * Gets the functions for skills and items.
     * @param caster the caster
     * @param target the target
     * @param skill the skill
     * @param item the item
     * @param owner the owner
     * @return the function if conditions are met, {@code null} otherwise
     */
    private AbstractFunction getFunc(Creature caster, Creature target, Skill skill, Item item, Object owner) {
        if ((attachCond != null) && !attachCond.test(caster, target, skill)) {
            return null;
        }

        try {
            return (AbstractFunction) constructor.newInstance(stat, order, owner, value, applyCond);
        } catch (Exception e) {
            log.warn("{}", e.getMessage());
        }

        return null;
    }
}
