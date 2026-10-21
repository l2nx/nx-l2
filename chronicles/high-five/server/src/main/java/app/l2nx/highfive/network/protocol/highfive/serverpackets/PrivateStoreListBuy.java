/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.TradeItem;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;

public class PrivateStoreListBuy extends AbstractItemPacket {
    private final int objId;
    private final long playerAdena;
    private final Collection<TradeItem> items;

    public PrivateStoreListBuy(Player player, Player storePlayer) {
        objId = storePlayer.getObjectId();
        playerAdena = player.getAdena();
        storePlayer.getSellList().updateItems(); // Update SellList for case inventory content has changed.
        items = storePlayer.getBuyList().getAvailableItems(player.getInventory());
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PRIVATE_STORE_BUY_LIST.writeId(this, buffer);
        buffer.writeInt(objId);
        buffer.writeLong(playerAdena);
        buffer.writeInt(items.size());
        for (TradeItem item : items) {
            writeItem(item, buffer);
            buffer.writeInt(item.getObjectId());
            buffer.writeLong(item.getPrice());
            buffer.writeLong(item.getItem().getReferencePrice() * 2);
            buffer.writeLong(item.getStoreCount());
        }
    }
}
