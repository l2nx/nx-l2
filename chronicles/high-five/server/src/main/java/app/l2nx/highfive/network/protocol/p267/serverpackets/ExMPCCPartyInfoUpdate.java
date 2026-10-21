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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author chris_00
 */
public class ExMPCCPartyInfoUpdate extends ServerPacket {
    private final int mode;
    private final int leaderOID;
    private final int memberCount;
    private final String name;

    /**
     * @param party
     * @param mode 0 = Remove, 1 = Add
     */
    public ExMPCCPartyInfoUpdate(Party party, int mode) {
        name = party.getLeader().getName();
        leaderOID = party.getLeaderObjectId();
        memberCount = party.getMemberCount();
        this.mode = mode;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_MPCC_PARTY_INFO_UPDATE.writeId(this, buffer);
        buffer.writeString(name);
        buffer.writeInt(leaderOID);
        buffer.writeInt(memberCount);
        buffer.writeInt(mode); // mode 0 = Remove Party, 1 = AddParty, maybe more...
    }
}
