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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Maktakien
 */
public class MoveToLocationInVehicle extends ServerPacket {
    private final int objectId;
    private final int boatId;
    private final Location destination;
    private final Location origin;

    /**
     * @param player
     * @param destination
     * @param origin
     */
    public MoveToLocationInVehicle(Player player, Location destination, Location origin) {
        objectId = player.getObjectId();
        boatId = player.getBoat().getObjectId();
        this.destination = destination;
        this.origin = origin;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.MOVE_TO_LOCATION_IN_VEHICLE.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(boatId);
        buffer.writeInt(destination.getX());
        buffer.writeInt(destination.getY());
        buffer.writeInt(destination.getZ());
        buffer.writeInt(origin.getX());
        buffer.writeInt(origin.getY());
        buffer.writeInt(origin.getZ());
    }
}
