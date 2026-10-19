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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.SiegeFlag;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Adry_85
 */
public class AreaFriendly implements ITargetTypeHandler {
    @Override
    public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target) {
        final List<WorldObject> targetList = new ArrayList<>();
        final Player player = creature.asPlayer();
        if (!checkTarget(player, target) && (skill.getCastRange() >= 0)) {
            player.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
            return targetList;
        }

        if (onlyFirst) {
            targetList.add(target);
            return targetList;
        }

        if (player.isInOlympiadMode()) {
            targetList.add(player);
            return targetList;
        }

        targetList.add(target); // Add target to target list
        if (target != null) {
            final int maxTargets = skill.getAffectLimit();
            World.forEachVisibleObjectInRange(target, Creature.class, skill.getAffectRange(), obj -> {
                if (!checkTarget(player, obj) || (obj == creature)) {
                    return;
                }

                if ((maxTargets > 0) && (targetList.size() >= maxTargets)) {
                    return;
                }

                targetList.add(obj);
            });
        }

        return targetList;
    }

    private boolean checkTarget(Player player, Creature target) {
        if ((target == null)
                || target.isAlikeDead()
                || target.isDoor()
                || (target instanceof SiegeFlag)
                || target.isNpc()) {
            return false;
        }

        if (!GeoEngine.getInstance().canSeeTarget(player, target)) {
            return false;
        }

        if (target.isPlayable()) {
            final Player targetPlayer = target.asPlayer();
            if (player == targetPlayer) {
                return true;
            }

            if (targetPlayer.inObserverMode() || targetPlayer.isInOlympiadMode()) {
                return false;
            }

            if (player.isInDuelWith(target)) {
                return false;
            }

            if (player.isAtWarWith(target)) {
                return false;
            }

            if (player.isInPartyWith(target)) {
                return true;
            }

            if (target.isInsideZone(ZoneId.PVP)) {
                return false;
            }

            if (player.isInClanWith(target) || player.isInAllyWith(target) || player.isInCommandChannelWith(target)) {
                return true;
            }

            if ((targetPlayer.getPvpFlag() > 0) || (targetPlayer.getKarma() > 0)) {
                return false;
            }
        }

        return true;
    }

    public class CharComparator implements Comparator<Creature> {
        @Override
        public int compare(Creature char1, Creature char2) {
            return Double.compare((char1.getCurrentHp() / char1.getMaxHp()), (char2.getCurrentHp() / char2.getMaxHp()));
        }
    }

    @Override
    public Enum<TargetType> getTargetType() {
        return TargetType.AREA_FRIENDLY;
    }
}
