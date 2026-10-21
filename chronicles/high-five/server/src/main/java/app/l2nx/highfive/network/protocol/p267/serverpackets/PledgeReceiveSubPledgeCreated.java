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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.Clan.SubPledge;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author -Wooden-
 */
@Slf4j
public class PledgeReceiveSubPledgeCreated extends ServerPacket {
    private final SubPledge subPledge;
    private final Clan clan;

    public PledgeReceiveSubPledgeCreated(SubPledge subPledge, Clan clan) {
        this.subPledge = subPledge;
        this.clan = clan;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PLEDGE_RECEIVE_SUB_PLEDGE_CREATED.writeId(this, buffer);
        buffer.writeInt(1);
        buffer.writeInt(subPledge.getId());
        buffer.writeString(subPledge.getName());
        buffer.writeString(getLeaderName());
    }

    private String getLeaderName() {
        final int leaderId = subPledge.getLeaderId();
        if ((subPledge.getId() == Clan.SUBUNIT_ACADEMY) || (leaderId == 0)) {
            return "";
        } else if (clan.getClanMember(leaderId) == null) {
            log.warn("SubPledgeLeader: {} is missing from clan: {}[{}]", leaderId, clan.getName(), clan.getId());
            return "";
        } else {
            return clan.getClanMember(leaderId).getName();
        }
    }
}
