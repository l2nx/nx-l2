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

import app.l2nx.highfive.data.sql.CrestTable;
import app.l2nx.highfive.entity.clan.Crest;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author -Wooden-
 */
public class ExPledgeEmblem extends ServerPacket {
    private final int crestId;
    private final byte[] data;

    public ExPledgeEmblem(int crestId) {
        this.crestId = crestId;
        final Crest crest = CrestTable.getInstance().getCrest(crestId);
        data = crest != null ? crest.getData() : null;
    }

    public ExPledgeEmblem(int crestId, byte[] data) {
        this.crestId = crestId;
        this.data = data;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_PLEDGE_EMBLEM.writeId(this, buffer);
        buffer.writeInt(0);
        buffer.writeInt(crestId);
        if (data != null) {
            buffer.writeInt(data.length);
            buffer.writeBytes(data);
        } else {
            buffer.writeInt(0);
        }
    }
}
