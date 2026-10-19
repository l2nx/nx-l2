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
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.Collection;

public class GMViewPledgeInfo extends ServerPacket {
    private final Clan clan;
    private final Player player;

    public GMViewPledgeInfo(Clan clan, Player player) {
        this.clan = clan;
        this.player = player;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.GM_VIEW_PLEDGE_INFO.writeId(this, buffer);
        buffer.writeString(player.getName());
        buffer.writeInt(clan.getId());
        buffer.writeInt(0);
        buffer.writeString(clan.getName());
        buffer.writeString(clan.getLeaderName());
        buffer.writeInt(clan.getCrestId()); // -> no, it's no longer used (nuocnam) fix by game
        buffer.writeInt(clan.getLevel());
        buffer.writeInt(clan.getCastleId());
        buffer.writeInt(clan.getHideoutId());
        buffer.writeInt(clan.getFortId());
        buffer.writeInt(clan.getRank());
        buffer.writeInt(clan.getReputationScore());
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(clan.getAllyId()); // c2
        buffer.writeString(clan.getAllyName()); // c2
        buffer.writeInt(clan.getAllyCrestId()); // c2
        buffer.writeInt(clan.isAtWar()); // c3
        buffer.writeInt(0); // T3 Unknown

        final Collection<ClanMember> members = clan.getMembers();
        buffer.writeInt(members.size());
        for (ClanMember member : members) {
            if (member != null) {
                buffer.writeString(member.getName());
                buffer.writeInt(member.getLevel());
                buffer.writeInt(member.getClassId());
                buffer.writeInt(member.getSex());
                buffer.writeInt(member.getRaceOrdinal());
                buffer.writeInt(member.isOnline() ? member.getObjectId() : 0);
                buffer.writeInt(member.getSponsor() != 0);
            }
        }
    }
}
