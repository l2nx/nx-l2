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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.mechanics.skill.enums.FlyType;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author KenM
 */
public class FlyToLocation extends ServerPacket {
    private final int destX;
    private final int destY;
    private final int destZ;
    private final int chaObjId;
    private final int orgX;
    private final int orgY;
    private final int orgZ;
    private final FlyType type;

    public FlyToLocation(Creature creature, int destX, int destY, int destZ, FlyType type) {
        chaObjId = creature.getObjectId();
        orgX = creature.getX();
        orgY = creature.getY();
        orgZ = creature.getZ();
        this.destX = destX;
        this.destY = destY;
        this.destZ = destZ;
        this.type = type;
        if (creature.isPlayer()) {
            creature.asPlayer().setBlinkActive(true);
        }
    }

    public FlyToLocation(Creature creature, ILocational dest, FlyType type) {
        this(creature, dest.getX(), dest.getY(), dest.getZ(), type);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.FLY_TO_LOCATION.writeId(this, buffer);
        buffer.writeInt(chaObjId);
        buffer.writeInt(destX);
        buffer.writeInt(destY);
        buffer.writeInt(destZ);
        buffer.writeInt(orgX);
        buffer.writeInt(orgY);
        buffer.writeInt(orgZ);
        buffer.writeInt(type.ordinal());
    }
}
