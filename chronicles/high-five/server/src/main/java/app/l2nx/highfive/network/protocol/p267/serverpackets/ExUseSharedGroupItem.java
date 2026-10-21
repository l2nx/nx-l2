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

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author KenM
 */
public class ExUseSharedGroupItem extends ServerPacket {
    private final int itemId;
    private final int grpId;
    private final int remainingTime;
    private final int totalTime;

    public ExUseSharedGroupItem(int itemId, int grpId, long remainingTime, int totalTime) {
        this.itemId = itemId;
        this.grpId = grpId;
        this.remainingTime = (int) (remainingTime / 1000);
        this.totalTime = totalTime / 1000;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_USE_SHARED_GROUP_ITEM.writeId(this, buffer);
        buffer.writeInt(itemId);
        buffer.writeInt(grpId);
        buffer.writeInt(remainingTime);
        buffer.writeInt(totalTime);
    }
}
