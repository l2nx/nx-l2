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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.AirShip;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.ExMoveToLocationInAirShip;
import app.l2nx.highfive.network.serverpackets.StopMoveInVehicle;

/**
 * format: ddddddd X:%d Y:%d Z:%d OriginX:%d OriginY:%d OriginZ:%d
 * @author GodKratos
 */
public class MoveToLocationInAirShip extends ClientPacket {
    private int shipId;
    private int targetX;
    private int targetY;
    private int targetZ;
    private int originX;
    private int originY;
    private int originZ;

    @Override
    protected void readImpl() {
        shipId = readInt();
        targetX = readInt();
        targetY = readInt();
        targetZ = readInt();
        originX = readInt();
        originY = readInt();
        originZ = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if ((targetX == originX) && (targetY == originY) && (targetZ == originZ)) {
            player.sendPacket(new StopMoveInVehicle(player, shipId));
            return;
        }

        if (player.isAttackingNow()
                && (player.getActiveWeaponItem() != null)
                && (player.getActiveWeaponItem().getItemType() == WeaponType.BOW)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isSitting() || player.isMovementDisabled()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (!player.isInAirShip()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final AirShip airShip = player.getAirShip();
        if (airShip.getObjectId() != shipId) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.setInVehiclePosition(new Location(targetX, targetY, targetZ));
        player.broadcastPacket(new ExMoveToLocationInAirShip(player));
    }
}
