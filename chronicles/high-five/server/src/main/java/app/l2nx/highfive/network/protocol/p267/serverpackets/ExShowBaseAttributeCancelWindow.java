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
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;

public class ExShowBaseAttributeCancelWindow extends ServerPacket {
    private final Collection<Item> items;
    private long price;

    public ExShowBaseAttributeCancelWindow(Player player) {
        items = player.getInventory().getElementItems();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_BASE_ATTRIBUTE_CANCEL_WINDOW.writeId(this, buffer);
        buffer.writeInt(items.size());
        for (Item item : items) {
            buffer.writeInt(item.getObjectId());
            buffer.writeLong(getPrice(item));
        }
    }

    /**
     * TODO: Update prices for Top/Mid/Low S80/S84
     * @param item
     * @return
     */
    private long getPrice(Item item) {
        switch (item.getTemplate().getCrystalType()) {
            case S: {
                if (item.getTemplate() instanceof Weapon) {
                    price = 50000;
                } else {
                    price = 40000;
                }
                break;
            }
            case S80: {
                if (item.getTemplate() instanceof Weapon) {
                    price = 100000;
                } else {
                    price = 80000;
                }
                break;
            }
            case S84: {
                if (item.getTemplate() instanceof Weapon) {
                    price = 200000;
                } else {
                    price = 160000;
                }
                break;
            }
        }

        return price;
    }
}
