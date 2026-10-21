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
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;

/**
 * Player Call Pc condition implementation.
 * @author Adry_85
 */
public class ConditionPlayerCallPc extends Condition {
    private final boolean value;

    public ConditionPlayerCallPc(boolean value) {
        this.value = value;
    }

    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        boolean canCallPlayer = true;
        final Player player = effector.asPlayer();
        if (player == null) {
            canCallPlayer = false;
        } else if ((effected != null) && effected.isPlayer() && effected.isDead()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_IS_DEAD_AT_THE_MOMENT_AND_CANNOT_BE_SUMMONED);
            sm.addPcName(effected.asPlayer());
            player.sendPacket(sm);
            canCallPlayer = false;
        } else if (player.isInOlympiadMode()) {
            player.sendPacket(SystemMessageId.YOU_MAY_NOT_SUMMON_FROM_YOUR_CURRENT_LOCATION);
            canCallPlayer = false;
        } else if (player.inObserverMode()) {
            canCallPlayer = false;
        } else if (player.isOnEvent()) {
            player.sendPacket(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING);
            canCallPlayer = false;
        } else if (player.isInsideZone(ZoneId.NO_SUMMON_FRIEND)
                || player.isInsideZone(ZoneId.JAIL)
                || player.isFlyingMounted()) {
            player.sendPacket(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING);
            canCallPlayer = false;
        }

        return value == canCallPlayer;
    }
}
