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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class PledgeShowMemberListAdd extends ServerPacket {
    private final String name;
    private final int level;
    private final int classId;
    private final int isOnline;
    private final int pledgeType;

    public PledgeShowMemberListAdd(Player player) {
        name = player.getName();
        level = player.getLevel();
        classId = player.getPlayerClass().getId();
        isOnline = (player.isOnline() ? player.getObjectId() : 0);
        pledgeType = player.getPledgeType();
    }

    public PledgeShowMemberListAdd(ClanMember cm) {
        name = cm.getName();
        level = cm.getLevel();
        classId = cm.getClassId();
        isOnline = (cm.isOnline() ? cm.getObjectId() : 0);
        pledgeType = cm.getPledgeType();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PLEDGE_SHOW_MEMBER_LIST_ADD.writeId(this, buffer);
        buffer.writeString(name);
        buffer.writeInt(level);
        buffer.writeInt(classId);
        buffer.writeInt(0);
        buffer.writeInt(1);
        buffer.writeInt(isOnline); // 1 = online 0 = offline
        buffer.writeInt(pledgeType);
    }
}
