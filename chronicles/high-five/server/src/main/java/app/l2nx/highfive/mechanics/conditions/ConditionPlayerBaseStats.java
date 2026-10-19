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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.BaseStat;

/**
 * The Class ConditionPlayerBaseStats.
 * @author mkizub
 */
public class ConditionPlayerBaseStats extends Condition {
    private final BaseStat stat;
    private final int value;

    /**
     * Instantiates a new condition player base stats.
     * @param creature the player
     * @param stat the stat
     * @param value the value
     */
    public ConditionPlayerBaseStats(Creature creature, BaseStat stat, int value) {
        super();
        this.stat = stat;
        this.value = value;
    }

    /**
     * Test impl.
     * @return true, if successful
     */
    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        final Player player = effector.asPlayer();
        if (player == null) {
            return false;
        }

        switch (stat) {
            case INT: {
                return player.getINT() >= value;
            }
            case STR: {
                return player.getSTR() >= value;
            }
            case CON: {
                return player.getCON() >= value;
            }
            case DEX: {
                return player.getDEX() >= value;
            }
            case MEN: {
                return player.getMEN() >= value;
            }
            case WIT: {
                return player.getWIT() >= value;
            }
        }

        return false;
    }
}
