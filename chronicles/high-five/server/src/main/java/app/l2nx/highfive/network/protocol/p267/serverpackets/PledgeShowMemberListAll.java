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
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.Clan.SubPledge;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;

public class PledgeShowMemberListAll extends ServerPacket {
    private final Clan clan;
    private final Player player;
    private final Collection<ClanMember> members;
    private int pledgeType;

    public PledgeShowMemberListAll(Clan clan, Player player) {
        this.clan = clan;
        this.player = player;
        members = this.clan.getMembers();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        pledgeType = 0;

        // FIXME: That's wrong on retail sends this whole packet few times (depending how much sub pledges it has)
        writePledge(0, buffer);
        for (SubPledge subPledge : clan.getAllSubPledges()) {
            player.sendPacket(new PledgeReceiveSubPledgeCreated(subPledge, clan));
        }

        for (ClanMember m : members) {
            if (m.getPledgeType() == 0) {
                continue;
            }

            player.sendPacket(new PledgeShowMemberListAdd(m));
        }

        // unless this is sent sometimes, the client doesn't recognise the player as the leader
        player.updateUserInfo();
    }

    private void writePledge(int mainOrSubpledge, PacketWriter buffer) {
        ServerPackets.PLEDGE_SHOW_MEMBER_LIST_ALL.writeId(this, buffer);
        buffer.writeInt(mainOrSubpledge);
        buffer.writeInt(clan.getId());
        buffer.writeInt(pledgeType);
        buffer.writeString(clan.getName());
        buffer.writeString(clan.getLeaderName());
        buffer.writeInt(clan.getCrestId()); // crest id .. is used again
        buffer.writeInt(clan.getLevel());
        buffer.writeInt(clan.getCastleId());
        buffer.writeInt(clan.getHideoutId());
        buffer.writeInt(clan.getFortId());
        buffer.writeInt(clan.getRank());
        buffer.writeInt(clan.getReputationScore());
        buffer.writeInt(0); // 0
        buffer.writeInt(0); // 0
        buffer.writeInt(clan.getAllyId());
        buffer.writeString(clan.getAllyName());
        buffer.writeInt(clan.getAllyCrestId());
        buffer.writeInt(clan.isAtWar()); // new c3
        buffer.writeInt(0); // Territory castle ID
        buffer.writeInt(clan.getSubPledgeMembersCount(pledgeType));
        for (ClanMember m : members) {
            if (m.getPledgeType() != pledgeType) {
                continue;
            }

            buffer.writeString(m.getName());
            buffer.writeInt(m.getLevel());
            buffer.writeInt(m.getClassId());
            final Player player = m.getPlayer();
            if (player != null) {
                buffer.writeInt(player.getAppearance().isFemale()); // no visible effect
                buffer.writeInt(player.getRace().ordinal()); // buffer.writeInt(1);
            } else {
                buffer.writeInt(1); // no visible effect
                buffer.writeInt(1); // buffer.writeInt(1);
            }

            buffer.writeInt(m.isOnline() ? m.getObjectId() : 0); // objectId = online 0 = offline
            buffer.writeInt(m.getSponsor() != 0);
        }
    }
}
