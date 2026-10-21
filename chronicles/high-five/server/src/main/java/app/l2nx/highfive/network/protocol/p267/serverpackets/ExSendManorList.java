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

import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Comparator;
import java.util.List;

/**
 * @author l3x
 */
public class ExSendManorList extends ServerPacket {
    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        final List<Castle> castles = CastleManager.getInstance().getCastles();
        castles.sort(Comparator.comparingInt(Castle::getResidenceId));
        ServerPackets.EX_SEND_MANOR_LIST.writeId(this, buffer);
        buffer.writeInt(castles.size());
        for (Castle castle : castles) {
            buffer.writeInt(castle.getResidenceId());
            buffer.writeString(castle.getName().toLowerCase());
        }
    }
}
