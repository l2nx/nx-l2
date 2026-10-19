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
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WareHouseWithdrawalList extends AbstractItemPacket {
    public static final int PRIVATE = 1;
    public static final int CLAN = 4;
    public static final int CASTLE = 3; // not sure
    public static final int FREIGHT = 1;

    private long playerAdena;
    private Collection<Item> items;
    /**
     * <ul>
     * <li>0x01-Private Warehouse</li>
     * <li>0x02-Clan Warehouse</li>
     * <li>0x03-Castle Warehouse</li>
     * <li>0x04-Warehouse</li>
     * </ul>
     */
    private int whType;

    public WareHouseWithdrawalList(Player player, int type) {
        if (player.getActiveWarehouse() == null) {
            log.warn("Error while sending withdraw request to: {}", player.getName());
            return;
        }

        playerAdena = player.getAdena();
        items = player.getActiveWarehouse().getItems();
        whType = type;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.WAREHOUSE_WITHDRAW_LIST.writeId(this, buffer);
        buffer.writeShort(whType);
        buffer.writeLong(playerAdena);
        buffer.writeShort(items.size());
        for (Item item : items) {
            writeItem(item, buffer);
            buffer.writeInt(item.getObjectId());
        }
    }
}
