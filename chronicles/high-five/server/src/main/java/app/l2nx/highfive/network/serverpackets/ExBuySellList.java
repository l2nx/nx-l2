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

import app.l2nx.highfive.config.custom.MerchantZeroSellPriceConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.Collection;

/**
 * @author ShanSoft
 */
public class ExBuySellList extends AbstractItemPacket {
    private final Collection<Item> sellList;
    private Collection<Item> refundList = null;
    private final boolean done;

    public ExBuySellList(Player player, boolean done) {
        sellList = player.getInventory().getAvailableItems(false, false, false);
        if (player.hasRefund()) {
            refundList = player.getRefund().getItems();
        }

        this.done = done;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_BUY_SELL_LIST.writeId(this, buffer);
        buffer.writeInt(1);
        if (sellList != null) {
            buffer.writeShort(sellList.size());
            for (Item item : sellList) {
                writeItem(item, buffer);
                buffer.writeLong(
                        MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE
                                ? 0
                                : item.getTemplate().getReferencePrice() / 2);
            }
        } else {
            buffer.writeShort(0);
        }

        if ((refundList != null) && !refundList.isEmpty()) {
            buffer.writeShort(refundList.size());
            int i = 0;
            for (Item item : refundList) {
                writeItem(item, buffer);
                buffer.writeInt(i++);
                buffer.writeLong(
                        MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE
                                ? 0
                                : (item.getTemplate().getReferencePrice() / 2) * item.getCount());
            }
        } else {
            buffer.writeShort(0);
        }

        buffer.writeByte(done);
    }
}
