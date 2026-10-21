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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * update 27.8.10
 * @author Kerberos, JIV
 */
public class ExValidateLocationInAirShip extends ServerPacket {
    private final Player player;
    private final int shipId;
    private final int x;
    private final int y;
    private final int z;
    private final int h;

    public ExValidateLocationInAirShip(Player player) {
        this.player = player;
        shipId = this.player.getAirShip().getObjectId();
        x = player.getInVehiclePosition().getX();
        y = player.getInVehiclePosition().getY();
        z = player.getInVehiclePosition().getZ();
        h = player.getHeading();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_VALIDATE_LOCATION_IN_AIRSHIP.writeId(this, buffer);
        buffer.writeInt(player.getObjectId());
        buffer.writeInt(shipId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(h);
    }
}
