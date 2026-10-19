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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoom;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author Gnacik
 */
public class ExPartyRoomMember extends ServerPacket {
    private final PartyMatchRoom room;
    private final int mode;

    public ExPartyRoomMember(PartyMatchRoom room, int mode) {
        this.room = room;
        this.mode = mode;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_PARTY_ROOM_MEMBER.writeId(this, buffer);
        buffer.writeInt(mode);
        buffer.writeInt(room.getMembers());
        for (Player member : room.getPartyMembers()) {
            buffer.writeInt(member.getObjectId());
            buffer.writeString(member.getName());
            buffer.writeInt(member.getActiveClass());
            buffer.writeInt(member.getLevel());
            buffer.writeInt(room.getLocation());
            if (room.getOwner().equals(member)) {
                buffer.writeInt(1);
            } else {
                if ((room.getOwner().isInParty() && member.isInParty())
                        && (room.getOwner().getParty().getLeaderObjectId()
                                == member.getParty().getLeaderObjectId())) {
                    buffer.writeInt(2);
                } else {
                    buffer.writeInt(0);
                }
            }

            buffer.writeInt(0); // TODO: Instance datas there is more if that is not 0!
        }
    }
}
