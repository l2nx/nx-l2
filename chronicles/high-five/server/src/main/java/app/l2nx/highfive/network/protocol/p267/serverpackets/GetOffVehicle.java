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

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Maktakien
 */
public class GetOffVehicle extends ServerPacket {
    private final int objectId;
    private final int boatObjId;
    private final int x;
    private final int y;
    private final int z;

    /**
     * @param charObjId
     * @param boatObjId
     * @param x
     * @param y
     * @param z
     */
    public GetOffVehicle(int charObjId, int boatObjId, int x, int y, int z) {
        objectId = charObjId;
        this.boatObjId = boatObjId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.GET_OFF_VEHICLE.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(boatObjId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }
}
