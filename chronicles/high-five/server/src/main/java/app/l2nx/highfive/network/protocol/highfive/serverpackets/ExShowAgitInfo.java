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

import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Map;

/**
 * @author KenM
 */
public class ExShowAgitInfo extends ServerPacket {
    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_AGIT_INFO.writeId(this, buffer);
        final Map<Integer, AuctionableHall> clannhalls =
                ClanHallTable.getInstance().getAllAuctionableClanHalls();
        buffer.writeInt(clannhalls.size());
        for (AuctionableHall ch : clannhalls.values()) {
            buffer.writeInt(ch.getId());
            buffer.writeString(
                    ch.getOwnerId() <= 0
                            ? ""
                            : ClanTable.getInstance().getClan(ch.getOwnerId()).getName()); // owner clan name
            buffer.writeString(
                    ch.getOwnerId() <= 0
                            ? ""
                            : ClanTable.getInstance().getClan(ch.getOwnerId()).getLeaderName()); // leader name
            buffer.writeInt(ch.getGrade() < 1); // 0 - auction 1 - war clanhall 2 - ETC (rainbow spring clanhall)
        }
    }
}
