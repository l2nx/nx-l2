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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.GetOffVehicle;
import app.l2nx.highfive.network.serverpackets.StopMoveInVehicle;

/**
 * @author Maktakien
 */
public class RequestGetOffVehicle extends ClientPacket {
    private int boatId;
    private int x;
    private int y;
    private int z;

    @Override
    protected void readImpl() {
        boatId = readInt();
        x = readInt();
        y = readInt();
        z = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!player.isInBoat()
                || (player.getBoat().getObjectId() != boatId)
                || player.getBoat().isMoving()
                || !player.isInsideRadius3D(x, y, z, 1000)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.broadcastPacket(new StopMoveInVehicle(player, boatId));
        player.setVehicle(null);
        player.setInVehiclePosition(null);
        player.sendPacket(ActionFailed.STATIC_PACKET);
        player.broadcastPacket(new GetOffVehicle(player.getObjectId(), boatId, x, y, z));
        player.setXYZ(x, y, z);
        player.setInsideZone(ZoneId.PEACE, false);
        player.revalidateZone(true);
    }
}
