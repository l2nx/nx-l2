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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import lombok.Getter;

/**
 * A mother-trees zone Basic type zone for Hp, MP regen
 * @author durgus
 */
public class MotherTreeZone extends ZoneType {
    private int enterMsg;
    private int leaveMsg;

    @Getter
    private int mpRegenBonus;

    @Getter
    private int hpRegenBonus;

    public MotherTreeZone(int id) {
        super(id);
    }

    @Override
    public void setParameter(String name, String value) {
        if (name.equals("enterMsgId")) {
            enterMsg = Integer.parseInt(value);
        } else if (name.equals("leaveMsgId")) {
            leaveMsg = Integer.parseInt(value);
        } else if (name.equals("MpRegenBonus")) {
            mpRegenBonus = Integer.parseInt(value);
        } else if (name.equals("HpRegenBonus")) {
            hpRegenBonus = Integer.parseInt(value);
        } else {
            super.setParameter(name, value);
        }
    }

    @Override
    protected void onEnter(Creature creature) {
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            creature.setInsideZone(ZoneId.MOTHER_TREE, true);
            if (enterMsg != 0) {
                player.sendPacket(new SystemMessage(enterMsg));
            }
        }
    }

    @Override
    protected void onExit(Creature creature) {
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            player.setInsideZone(ZoneId.MOTHER_TREE, false);
            if (leaveMsg != 0) {
                player.sendPacket(new SystemMessage(leaveMsg));
            }
        }
    }
}
