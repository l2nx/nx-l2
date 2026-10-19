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

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author UnAfraid
 */
public class AreaSummon implements ITargetTypeHandler {
    @Override
    public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target) {
        final List<WorldObject> targetList = new ArrayList<>();
        final Creature targetCreature =
                creature.isPlayer() ? creature.asPlayer().getSummon() : null;
        if ((targetCreature == null) || !targetCreature.isServitor() || targetCreature.isDead()) {
            return targetList;
        }

        if (onlyFirst) {
            targetList.add(targetCreature);
            return targetList;
        }

        final boolean srcInArena = (creature.isInsideZone(ZoneId.PVP) && !creature.isInsideZone(ZoneId.SIEGE));
        final int maxTargets = skill.getAffectLimit();
        World.forEachVisibleObjectInRange(targetCreature, Creature.class, skill.getAffectRange(), obj -> {
            if (obj == creature) {
                return;
            }

            if (!(obj.isAttackable() || obj.isPlayable())) {
                return;
            }

            if (!Skill.checkForAreaOffensiveSkills(creature, obj, skill, srcInArena)) {
                return;
            }

            if ((maxTargets > 0) && (targetList.size() >= maxTargets)) {
                return;
            }

            targetList.add(obj);
        });

        return targetList;
    }

    @Override
    public Enum<TargetType> getTargetType() {
        return TargetType.AREA_SUMMON;
    }
}
