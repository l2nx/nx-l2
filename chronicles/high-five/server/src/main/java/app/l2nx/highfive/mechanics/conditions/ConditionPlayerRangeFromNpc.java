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

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import java.util.Set;

/**
 * Exist NPC condition.
 * @author UnAfraid
 */
public class ConditionPlayerRangeFromNpc extends Condition {
    /** NPC Ids. */
    private final Set<Integer> npcIds;
    /** Radius to check. */
    private final int radius;
    /** Expected value. */
    private final boolean value;

    public ConditionPlayerRangeFromNpc(Set<Integer> npcIds, int radius, boolean value) {
        this.npcIds = npcIds;
        this.radius = radius;
        this.value = value;
    }

    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        boolean existNpc = false;
        if (!npcIds.isEmpty() && (radius > 0)) {
            existNpc = World.getFirstVisibleObjectInRange(
                            effector, Npc.class, radius, target -> npcIds.contains(target.getId()))
                    != null;
        }

        return existNpc == value;
    }
}
