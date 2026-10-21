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
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;

/**
 * @author KenM
 */
@Slf4j
public class ExShowCastleInfo extends ServerPacket {
    private final Collection<Castle> castles;

    public ExShowCastleInfo() {
        castles = CastleManager.getInstance().getCastles();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_CASTLE_INFO.writeId(this, buffer);
        buffer.writeInt(castles.size());
        for (Castle castle : castles) {
            buffer.writeInt(castle.getResidenceId());
            if (castle.getOwnerId() > 0) {
                if (ClanTable.getInstance().getClan(castle.getOwnerId()) != null) {
                    buffer.writeString(
                            ClanTable.getInstance().getClan(castle.getOwnerId()).getName());
                } else {
                    log.warn(
                            "Castle owner with no name! Castle: {} has an OwnerId = {} who does not have a  name!",
                            castle.getName(),
                            castle.getOwnerId());
                    buffer.writeString("");
                }
            } else {
                buffer.writeString("");
            }

            buffer.writeInt(castle.getTaxPercent());
            buffer.writeInt((int) (castle.getSiege().getSiegeDate().getTimeInMillis() / 1000));
        }
    }
}
