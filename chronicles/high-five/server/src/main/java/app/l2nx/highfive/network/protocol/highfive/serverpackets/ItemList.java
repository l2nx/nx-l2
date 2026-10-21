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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ItemList extends AbstractItemPacket {
    private final Player player;
    private final boolean showWindow;
    private final List<Item> items = new ArrayList<>();

    public ItemList(Player player, boolean showWindow) {
        this.player = player;
        this.showWindow = showWindow;
        for (Item item : player.getInventory().getItems()) {
            if (!item.isQuestItem()) {
                items.add(item);
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.ITEM_LIST.writeId(this, buffer);
        buffer.writeShort(showWindow);
        buffer.writeShort(items.size());
        for (Item item : items) {
            writeItem(item, buffer);
        }

        writeInventoryBlock(player.getInventory(), buffer);
    }

    @Override
    public void runImpl(Player player) {
        if (player != null) {
            player.sendPacket(new ExQuestItemList(this.player));
        }
    }
}
