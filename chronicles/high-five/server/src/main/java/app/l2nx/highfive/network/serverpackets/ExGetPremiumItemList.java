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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.holders.PremiumItem;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.Map.Entry;

/**
 * @author Gnacik
 */
public class ExGetPremiumItemList extends ServerPacket {
    private final Player _player;

    public ExGetPremiumItemList(Player player) {
        _player = player;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_GET_PREMIUM_ITEM_LIST.writeId(this, buffer);
        buffer.writeInt(_player.getPremiumItemList().size());
        for (Entry<Integer, PremiumItem> entry : _player.getPremiumItemList().entrySet()) {
            final PremiumItem item = entry.getValue();
            buffer.writeInt(entry.getKey());
            buffer.writeInt(_player.getObjectId());
            buffer.writeInt(item.getItemId());
            buffer.writeLong(item.getCount());
            buffer.writeInt(0); // ?
            buffer.writeString(item.getSender());
        }
    }
}
