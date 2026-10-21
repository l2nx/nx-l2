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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

public class PartySmallWindowAdd extends ServerPacket {
    private final Player member;
    private final Party party;

    public PartySmallWindowAdd(Player member, Party party) {
        this.member = member;
        this.party = party;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.PARTY_SMALL_WINDOW_ADD.writeId(this, buffer);
        buffer.writeInt(party.getLeaderObjectId()); // c3
        buffer.writeInt(party.getDistributionType().getId()); // buffer.writeInt(4); ?? //c3
        buffer.writeInt(member.getObjectId());
        buffer.writeString(member.getName());
        buffer.writeInt((int) member.getCurrentCp()); // c4
        buffer.writeInt(member.getMaxCp()); // c4
        buffer.writeInt((int) member.getCurrentHp());
        buffer.writeInt(member.getMaxHp());
        buffer.writeInt((int) member.getCurrentMp());
        buffer.writeInt(member.getMaxMp());
        buffer.writeInt(member.getLevel());
        buffer.writeInt(member.getPlayerClass().getId());
        buffer.writeInt(0); // ?
        buffer.writeInt(0); // ?
    }
}
