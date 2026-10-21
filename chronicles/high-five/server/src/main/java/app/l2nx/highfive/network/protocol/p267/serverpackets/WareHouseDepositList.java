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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class WareHouseDepositList extends AbstractItemPacket {
    public static final int PRIVATE = 1;
    public static final int CLAN = 4;
    public static final int CASTLE = 3; // not sure
    public static final int FREIGHT = 1;

    private final long playerAdena;
    private final List<Item> items = new ArrayList<>();
    /**
     * <ul>
     * <li>0x01-Private Warehouse</li>
     * <li>0x02-Clan Warehouse</li>
     * <li>0x03-Castle Warehouse</li>
     * <li>0x04-Warehouse</li>
     * </ul>
     */
    private final int whType;

    public WareHouseDepositList(Player player, int type) {
        whType = type;
        playerAdena = player.getAdena();
        final boolean isPrivate = whType == PRIVATE;
        for (Item temp : player.getInventory().getAvailableItems(true, isPrivate, false)) {
            if ((temp != null) && temp.isDepositable(isPrivate)) {
                items.add(temp);
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.WAREHOUSE_DEPOSIT_LIST.writeId(this, buffer);
        buffer.writeShort(whType);
        buffer.writeLong(playerAdena);
        buffer.writeShort(items.size());
        for (Item item : items) {
            writeItem(item, buffer);
            buffer.writeInt(item.getObjectId());
        }
    }
}
