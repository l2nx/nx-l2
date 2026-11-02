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
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author -Wooden-
 */
public class PledgeReceiveWarList extends ServerPacket {
    private final Clan clan;
    private final int tab;

    public PledgeReceiveWarList(Clan clan, int tab) {
        this.clan = clan;
        this.tab = tab;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PLEDGE_RECEIVE_WAR_LIST.writeId(this, buffer);
        buffer.writeInt(tab); // type : 0 = Declared, 1 = Under Attack
        buffer.writeInt(0); // page
        try (IntSnapshot clanIds = ScratchBuffers.intEmpty();
                Snapshot<Clan> clans = ScratchBuffers.empty()) {
            if (tab == 0) {
                this.clan.collectWarList(clanIds);
            } else {
                this.clan.collectAttackerList(clanIds);
            }

            for (int i = 0; i < clanIds.size(); i++) {
                final Clan clan = ClanTable.getInstance().getClan(clanIds.getInt(i));
                if (clan != null) {
                    clans.add(clan);
                }
            }

            buffer.writeInt(clans.size());
            for (Clan clan : clans) {
                buffer.writeString(clan.getName());
                buffer.writeInt(tab); // ??
                buffer.writeInt(tab); // ??
            }
        }
    }
}
