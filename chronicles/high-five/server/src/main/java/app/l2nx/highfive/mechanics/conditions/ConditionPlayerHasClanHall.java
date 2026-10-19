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
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import java.util.List;

/**
 * The Class ConditionPlayerHasClanHall.
 * @author MrPoke
 */
public class ConditionPlayerHasClanHall extends Condition {
    private final List<Integer> clanHall;

    /**
     * Instantiates a new condition player has clan hall.
     * @param clanHall the clan hall
     */
    public ConditionPlayerHasClanHall(List<Integer> clanHall) {
        this.clanHall = clanHall;
    }

    /**
     * Test impl.
     * @return true, if successful
     */
    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        if (!effector.isPlayer()) {
            return false;
        }

        final Clan clan = effector.asPlayer().getClan();
        if (clan == null) {
            return (clanHall.size() == 1) && (clanHall.get(0) == 0);
        }

        // All Clan Hall
        if ((clanHall.size() == 1) && (clanHall.get(0) == -1)) {
            return clan.getHideoutId() > 0;
        }

        return clanHall.contains(clan.getHideoutId());
    }
}
