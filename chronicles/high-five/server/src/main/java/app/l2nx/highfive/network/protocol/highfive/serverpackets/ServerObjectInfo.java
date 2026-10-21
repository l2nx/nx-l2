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
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author devScarlet, mrTJO
 */
public class ServerObjectInfo extends ServerPacket {
    private final Npc activeChar;
    private final int x;
    private final int y;
    private final int z;
    private final int heading;
    private final int displayId;
    private final boolean attackable;
    private final double collisionHeight;
    private final double collisionRadius;
    private final String name;

    public ServerObjectInfo(Npc activeChar, Creature actor) {
        this.activeChar = activeChar;
        displayId = this.activeChar.getTemplate().getDisplayId();
        attackable = this.activeChar.isAutoAttackable(actor);
        collisionHeight = this.activeChar.getCollisionHeight();
        collisionRadius = this.activeChar.getCollisionRadius();
        x = this.activeChar.getX();
        y = this.activeChar.getY();
        z = this.activeChar.getZ();
        heading = this.activeChar.getHeading();
        name = this.activeChar.getTemplate().isUsingServerSideName()
                ? this.activeChar.getTemplate().getName()
                : "";
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SERVER_OBJECT_INFO.writeId(this, buffer);
        buffer.writeInt(activeChar.getObjectId());
        buffer.writeInt(displayId + 1000000);
        buffer.writeString(name); // name
        buffer.writeInt(attackable);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(heading);
        buffer.writeDouble(1.0); // movement multiplier
        buffer.writeDouble(1.0); // attack speed multiplier
        buffer.writeDouble(collisionRadius);
        buffer.writeDouble(collisionHeight);
        buffer.writeInt((int) (attackable ? activeChar.getCurrentHp() : 0));
        buffer.writeInt(attackable ? activeChar.getMaxHp() : 0);
        buffer.writeInt(1); // object type
        buffer.writeInt(0); // special effects
    }
}
