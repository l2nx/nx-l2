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
package app.l2nx.highfive.scripts.handlers.skill.targets;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Chest;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;
import java.util.Collections;
import java.util.List;

/**
 * @author UnAfraid
 */
public class Unlockable implements ITargetTypeHandler {
    @Override
    public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target) {
        if ((target == null) || (!target.isDoor() && !(target instanceof Chest))) {
            creature.sendPacket(SystemMessageId.INVALID_TARGET);
            return Collections.emptyList();
        }

        return Collections.singletonList(target);
    }

    @Override
    public Enum<TargetType> getTargetType() {
        return TargetType.UNLOCKABLE;
    }
}
