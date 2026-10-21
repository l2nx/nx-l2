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
import app.l2nx.highfive.network.serverpackets.StopMoveInVehicle;

/**
 * @author Maktakien
 */
public class CannotMoveAnymoreInVehicle extends ClientPacket {
    private int x;
    private int y;
    private int z;
    private int heading;
    private int boatId;

    @Override
    protected void readImpl() {
        boatId = readInt();
        x = readInt();
        y = readInt();
        z = readInt();
        heading = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (player.isInBoat() && (player.getBoat().getObjectId() == boatId)) {
            player.setInVehiclePosition(new Location(x, y, z));
            player.setHeading(heading);
            player.broadcastPacket(new StopMoveInVehicle(player, boatId));
        }
    }
}
