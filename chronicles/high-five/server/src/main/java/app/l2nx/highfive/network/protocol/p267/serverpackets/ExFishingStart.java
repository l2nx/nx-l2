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
 * @author -Wooden-
 */
public class ExFishingStart extends ServerPacket {
    private final Creature creature;
    private final int x;
    private final int y;
    private final int z;
    private final int fishType;
    private final boolean nightLure;

    public ExFishingStart(Creature creature, int fishType, int x, int y, int z, boolean isNightLure) {
        this.creature = creature;
        this.fishType = fishType;
        this.x = x;
        this.y = y;
        this.z = z;
        nightLure = isNightLure;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_FISHING_START.writeId(this, buffer);
        buffer.writeInt(creature.getObjectId());
        buffer.writeInt(fishType); // fish type
        buffer.writeInt(x); // x position
        buffer.writeInt(y); // y position
        buffer.writeInt(z); // z position
        buffer.writeByte(nightLure); // night lure
        buffer.writeByte(0); // show fish rank result button
    }
}
