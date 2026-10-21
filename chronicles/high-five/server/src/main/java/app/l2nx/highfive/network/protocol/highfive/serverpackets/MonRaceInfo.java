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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class MonRaceInfo extends ServerPacket {
    private final int unknown1;
    private final int unknown2;
    private final Npc[] monsters;
    private final int[][] speeds;

    public MonRaceInfo(int unknown1, int unknown2, Npc[] monsters, int[][] speeds) {
        /*
         * -1 0 to initial the race 0 15322 to start race 13765 -1 in middle of race -1 0 to end the race
         */
        this.unknown1 = unknown1;
        this.unknown2 = unknown2;
        this.monsters = monsters;
        this.speeds = speeds;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.MON_RACE_INFO.writeId(this, buffer);
        buffer.writeInt(unknown1);
        buffer.writeInt(unknown2);
        buffer.writeInt(8);
        for (int i = 0; i < 8; i++) {
            buffer.writeInt(monsters[i].getObjectId()); // npcObjectID
            buffer.writeInt(monsters[i].getTemplate().getDisplayId() + 1000000); // npcID
            buffer.writeInt(14107); // origin X
            buffer.writeInt(181875 + (58 * (7 - i))); // origin Y
            buffer.writeInt(-3566); // origin Z
            buffer.writeInt(12080); // end X
            buffer.writeInt(181875 + (58 * (7 - i))); // end Y
            buffer.writeInt(-3566); // end Z
            buffer.writeDouble(monsters[i].getTemplate().getCollisionHeight());
            buffer.writeDouble(monsters[i].getTemplate().getCollisionRadius());
            buffer.writeInt(120); // ?? unknown
            for (int j = 0; j < 20; j++) {
                if (unknown1 == 0) {
                    buffer.writeByte(speeds[i][j]);
                } else {
                    buffer.writeByte(0);
                }
            }

            buffer.writeInt(0);
            buffer.writeInt(0); // CT2.3 special effect
        }
    }
}
