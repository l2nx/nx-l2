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
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class GMViewItemList extends AbstractItemPacket {
    private final List<Item> items = new ArrayList<>();
    private final int limit;
    private final String playerName;

    public GMViewItemList(Player player) {
        playerName = player.getName();
        limit = player.getInventoryLimit();
        for (Item item : player.getInventory().getItems()) {
            items.add(item);
        }
    }

    public GMViewItemList(Pet cha) {
        playerName = cha.getName();
        limit = cha.getInventoryLimit();
        for (Item item : cha.getInventory().getItems()) {
            items.add(item);
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.GM_VIEW_ITEM_LIST.writeId(this, buffer);
        buffer.writeString(playerName);
        buffer.writeInt(limit); // inventory limit
        buffer.writeShort(1); // show window ??
        buffer.writeShort(items.size());
        for (Item item : items) {
            writeItem(item, buffer);
        }
    }
}
