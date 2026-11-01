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

import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.network.holders.TradeItem;
import app.l2nx.network.codec.PacketWriter;

public abstract class AbstractItemPacket extends ServerPacket {
    protected void writeItem(TradeItem item, PacketWriter buffer) {
        writeItem(new ItemInfo(item), buffer);
    }

    protected void writeItem(Item item, PacketWriter buffer) {
        writeItem(new ItemInfo(item), buffer);
    }

    protected void writeTradeItem(TradeItem item, PacketWriter buffer) {
        buffer.writeShort(item.getItem().getType1());
        buffer.writeInt(item.getObjectId());
        buffer.writeInt(item.getItem().getDisplayId());
        buffer.writeLong(item.getCount());
        buffer.writeShort(item.getItem().getType2());
        buffer.writeShort(item.getCustomType1());
        buffer.writeInt(item.getItem().getBodyPart().getMask());
        buffer.writeShort(item.getEnchant());
        buffer.writeShort(0);
        buffer.writeShort(item.getCustomType2());
        writeItemElementalAndEnchant(new ItemInfo(item), buffer);
    }

    protected void writeItem(ItemInfo item, PacketWriter buffer) {
        buffer.writeInt(item.getObjectId());
        buffer.writeInt(item.getItem().getDisplayId());
        buffer.writeInt(item.getLocation());
        buffer.writeLong(item.getCount());
        buffer.writeShort(item.getItem().getType2());
        buffer.writeShort(item.getCustomType1());
        buffer.writeShort(item.getEquipped());
        buffer.writeInt(item.getItem().getBodyPart().getMask());
        // For a pet control item the enchant slot carries the pet level.
        buffer.writeShort(item.getEnchant());
        buffer.writeShort(item.getCustomType2());
        buffer.writeInt(item.getAugmentationBonus());
        buffer.writeInt(item.getMana());
        buffer.writeInt(item.getTime());
        writeItemElementalAndEnchant(item, buffer);
    }

    protected void writeItemElementalAndEnchant(ItemInfo item, PacketWriter buffer) {
        buffer.writeShort(item.getAttackElementType());
        buffer.writeShort(item.getAttackElementPower());
        for (byte i = 0; i < 6; i++) {
            buffer.writeShort(item.getElementDefAttr(i));
        }

        for (int op : item.getEnchantOptions()) {
            buffer.writeShort(op);
        }
    }

    protected void writeInventoryBlock(PlayerInventory inventory, PacketWriter buffer) {
        if (inventory.hasInventoryBlock()) {
            final int count = inventory.getBlockItemCount();
            buffer.writeShort(count);
            buffer.writeByte(inventory.getBlockMode());
            for (int i = 0; i < count; i++) {
                buffer.writeInt(inventory.getBlockItem(i));
            }
        } else {
            buffer.writeShort(0);
        }
    }
}
