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
import app.l2nx.highfive.mechanics.skill.Skill;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * The Class Condition.
 * @author mkizub
 */
public abstract class Condition implements ConditionListener {
    @Getter(AccessLevel.PACKAGE)
    private ConditionListener listener;

    @Getter
    @Setter
    private String message;

    @Getter
    @Setter
    private int messageId;

    @Getter
    private boolean addName = false;

    private boolean result;

    /**
     * Adds the name.
     */
    public void addName() {
        addName = true;
    }

    /**
     * Sets the listener.
     * @param listener the new listener
     */
    void setListener(ConditionListener listener) {
        this.listener = listener;
        notifyChanged();
    }

    public boolean test(Creature caster, Creature target, Skill skill) {
        return test(caster, target, skill, null);
    }

    public boolean test(Creature caster, Creature target, ItemTemplate item) {
        return test(caster, target, null, null);
    }

    public boolean test(Creature caster, Creature target, Skill skill, ItemTemplate item) {
        final boolean res = testImpl(caster, target, skill, item);
        if ((listener != null) && (res != result)) {
            result = res;
            notifyChanged();
        }

        return res;
    }

    /**
     * Test the condition.
     * @param effector the effector
     * @param effected the effected
     * @param skill the skill
     * @param item the item
     * @return {@code true} if successful, {@code false} otherwise
     */
    public abstract boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item);

    @Override
    public void notifyChanged() {
        if (listener != null) {
            listener.notifyChanged();
        }
    }
}
