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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Maktakien
 */
public class VehicleCheckLocation extends ServerPacket {
    private final Creature boat;

    public VehicleCheckLocation(Creature boat) {
        this.boat = boat;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.VEHICLE_CHECK_LOCATION.writeId(this, buffer);
        buffer.writeInt(boat.getObjectId());
        buffer.writeInt(boat.getX());
        buffer.writeInt(boat.getY());
        buffer.writeInt(boat.getZ());
        buffer.writeInt(boat.getHeading());
    }
}
