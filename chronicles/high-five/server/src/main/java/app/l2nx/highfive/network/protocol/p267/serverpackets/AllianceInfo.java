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

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanInfo;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestAllyInfo;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;
import lombok.Getter;

/**
 * Sent in response to {@link RequestAllyInfo}, if applicable.<br>
 * @author afk5min
 */
public class AllianceInfo extends ServerPacket {
    @Getter
    private final String name;

    @Getter
    private final int total;

    @Getter
    private final int online;

    @Getter
    private final String leaderC;

    @Getter
    private final String leaderP;

    @Getter
    private final ClanInfo[] allies;

    public AllianceInfo(int allianceId) {
        final Clan leader = ClanTable.getInstance().getClan(allianceId);
        name = leader.getAllyName();
        leaderC = leader.getName();
        leaderP = leader.getLeaderName();
        final Collection<Clan> allies = ClanTable.getInstance().getClanAllies(allianceId);
        this.allies = new ClanInfo[allies.size()];
        int idx = 0;
        int total = 0;
        int online = 0;
        for (Clan clan : allies) {
            final ClanInfo ci = new ClanInfo(clan);
            this.allies[idx++] = ci;
            total += ci.getTotal();
            online += ci.getOnline();
        }

        this.total = total;
        this.online = online;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.ALLIANCE_INFO.writeId(this, buffer);
        buffer.writeString(name);
        buffer.writeInt(total);
        buffer.writeInt(online);
        buffer.writeString(leaderC);
        buffer.writeString(leaderP);
        buffer.writeInt(allies.length);
        for (ClanInfo aci : allies) {
            buffer.writeString(aci.getClan().getName());
            buffer.writeInt(0);
            buffer.writeInt(aci.getClan().getLevel());
            buffer.writeString(aci.getClan().getLeaderName());
            buffer.writeInt(aci.getTotal());
            buffer.writeInt(aci.getOnline());
        }
    }
}
