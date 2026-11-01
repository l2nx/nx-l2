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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.holders.PremiumItem;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Map;
import java.util.Map.Entry;

public class ExGetPremiumItemList extends ServerPacket {
    private final Player player;

    public ExGetPremiumItemList(Player player) {
        this.player = player;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_GET_PREMIUM_ITEM_LIST.writeId(this, buffer);
        final Map<Integer, PremiumItem> premiumItems = player.getPremiumItemList();
        buffer.writeInt(premiumItems.size());
        for (Entry<Integer, PremiumItem> entry : premiumItems.entrySet()) {
            final PremiumItem item = entry.getValue();
            buffer.writeInt(entry.getKey());
            buffer.writeInt(player.getObjectId());
            buffer.writeInt(item.getItemId());
            buffer.writeLong(item.getCount());
            buffer.writeInt(0);
            buffer.writeString(item.getSender());
        }
    }
}
