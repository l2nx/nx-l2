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

import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.actor.instance.Servitor;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @version $Revision: 1.5.2.3.2.5 $ $Date: 2005/03/29 23:15:10 $
 */
public class PetStatusUpdate extends ServerPacket {
    private final Summon summon;
    private int maxFed;
    private int curFed;

    public PetStatusUpdate(Summon summon) {
        this.summon = summon;
        if (this.summon.isPet()) {
            final Pet pet = this.summon.asPet();
            curFed = pet.getCurrentFed(); // how fed it is
            maxFed = pet.getMaxFed(); // max fed it can be
        } else if (this.summon.isServitor()) {
            final Servitor sum = this.summon.asServitor();
            curFed = sum.getLifeTimeRemaining();
            maxFed = sum.getLifeTime();
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PET_STATUS_UPDATE.writeId(this, buffer);
        buffer.writeInt(summon.getSummonType());
        buffer.writeInt(summon.getObjectId());
        buffer.writeInt(summon.getX());
        buffer.writeInt(summon.getY());
        buffer.writeInt(summon.getZ());
        buffer.writeString(summon.getTitle());
        buffer.writeInt(curFed);
        buffer.writeInt(maxFed);
        buffer.writeInt((int) summon.getCurrentHp());
        buffer.writeInt(summon.getMaxHp());
        buffer.writeInt((int) summon.getCurrentMp());
        buffer.writeInt(summon.getMaxMp());
        buffer.writeInt(summon.getLevel());
        buffer.writeLong(summon.getStat().getExp());
        buffer.writeLong(summon.getExpForThisLevel()); // 0% absolute value
        buffer.writeLong(summon.getExpForNextLevel()); // 100% absolute value
    }
}
