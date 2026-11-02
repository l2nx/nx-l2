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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoom;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoomList;
import app.l2nx.highfive.entity.groups.matching.PartyMatchWaitingList;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPartyRoomMember;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartyMatchDetail;

/**
 * @author Gnacik
 */
public class RequestPartyMatchList extends ClientPacket {
    private int roomid;
    private int membersmax;
    private int minLevel;
    private int maxLevel;
    private int loot;
    private String roomtitle;

    @Override
    protected void readImpl() {
        roomid = readInt();
        membersmax = readInt();
        minLevel = readInt();
        maxLevel = readInt();
        loot = readInt();
        roomtitle = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (roomid > 0) {
            final PartyMatchRoom room = PartyMatchRoomList.getInstance().getRoom(roomid);
            if (room == null) {
                return;
            }

            if (!room.changeSettings(player, membersmax, minLevel, maxLevel, loot, roomtitle)) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            for (Player member : room.getPartyMembers()) {
                if (member == null) {
                    continue;
                }

                member.sendPacket(new PartyMatchDetail(room));
                member.sendPacket(SystemMessageId.THE_PARTY_ROOM_S_INFORMATION_HAS_BEEN_REVISED);
            }
        } else {
            final int maxid = PartyMatchRoomList.getInstance().getMaxId();
            final PartyMatchRoom room =
                    new PartyMatchRoom(maxid, roomtitle, loot, minLevel, maxLevel, membersmax, player);

            // PacketLogger.info("PartyMatchRoom #" + maxid + " created by " + player.getName());
            // Remove from waiting list
            PartyMatchWaitingList.getInstance().removePlayer(player);

            PartyMatchRoomList.getInstance().addPartyMatchRoom(maxid, room);
            if (player.isInParty()) {
                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    player.getParty().collectMembers(members);
                    for (Player ptmember : members) {
                        if (ptmember == null) {
                            continue;
                        }

                        if (ptmember == player) {
                            continue;
                        }

                        ptmember.setPartyRoom(maxid);
                        // ptmember.setPartyMatching(1);
                        room.addMember(ptmember);
                    }
                }
            }

            player.sendPacket(new PartyMatchDetail(room));
            player.sendPacket(new ExPartyRoomMember(room, 1));
            player.sendPacket(SystemMessageId.YOU_HAVE_CREATED_A_PARTY_ROOM);

            player.setPartyRoom(maxid);
            // _activeChar.setPartyMatching(1);
            player.broadcastUserInfo();
        }
    }
}
