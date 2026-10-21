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

import app.l2nx.highfive.data.sql.ClanTable;
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
        buffer.writeInt(
                tab == 0
                        ? this.clan.getWarList().size()
                        : this.clan.getAttackerList().size());
        for (Integer i : tab == 0 ? this.clan.getWarList() : this.clan.getAttackerList()) {
            final Clan clan = ClanTable.getInstance().getClan(i);
            if (clan == null) {
                continue;
            }

            buffer.writeString(clan.getName());
            buffer.writeInt(tab); // ??
            buffer.writeInt(tab); // ??
        }
    }
}
