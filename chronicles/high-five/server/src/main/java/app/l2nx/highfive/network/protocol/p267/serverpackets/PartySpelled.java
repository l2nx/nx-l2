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
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class PartySpelled extends ServerPacket {
    private final List<BuffInfo> effects = new ArrayList<>();
    private final Creature creature;

    public PartySpelled(Creature creature) {
        this.creature = creature;
    }

    public void addSkill(BuffInfo info) {
        effects.add(info);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PARTY_SPELLED.writeId(this, buffer);
        buffer.writeInt(creature.isServitor() ? 2 : creature.isPet() ? 1 : 0);
        buffer.writeInt(creature.getObjectId());
        buffer.writeInt(effects.size());
        for (BuffInfo info : effects) {
            if ((info != null) && info.isInUse()) {
                buffer.writeInt(info.getSkill().getDisplayId());
                buffer.writeShort(info.getSkill().getDisplayLevel());
                buffer.writeInt(info.getTime());
            }
        }
    }
}
