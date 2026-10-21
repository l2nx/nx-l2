/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class ExMoveToLocationAirShip extends ServerPacket {
    private final int objId;
    private final int tx;
    private final int ty;
    private final int tz;
    private final int x;
    private final int y;
    private final int z;

    public ExMoveToLocationAirShip(Creature creature) {
        objId = creature.getObjectId();
        tx = creature.getXdestination();
        ty = creature.getYdestination();
        tz = creature.getZdestination();
        x = creature.getX();
        y = creature.getY();
        z = creature.getZ();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_MOVE_TO_LOCATION_AIRSHIP.writeId(this, buffer);
        buffer.writeInt(objId);
        buffer.writeInt(tx);
        buffer.writeInt(ty);
        buffer.writeInt(tz);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }
}
