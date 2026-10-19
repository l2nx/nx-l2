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

/**
 * The Class ConditionTargetAggro.
 * @author mkizub
 */
public class ConditionTargetAggro extends Condition {
    private final boolean _isAggro;

    /**
     * Instantiates a new condition target aggro.
     * @param isAggro the is aggro
     */
    public ConditionTargetAggro(boolean isAggro) {
        _isAggro = isAggro;
    }

    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        if (effected != null) {
            if (effected.isMonster()) {
                return effected.asMonster().isAggressive() == _isAggro;
            }

            if (effected.isPlayer()) {
                return effected.asPlayer().getKarma() > 0;
            }
        }

        return false;
    }
}
