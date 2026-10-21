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

import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author KenM
 */
public class ExRpItemLink extends ServerPacket {
    private final Item item;

    public ExRpItemLink(Item item) {
        this.item = item;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_RP_ITEM_LINK.writeId(this, buffer);
        buffer.writeInt(item.getObjectId());
        buffer.writeInt(item.getDisplayId());
        buffer.writeInt(item.getLocationSlot());
        buffer.writeLong(item.getCount());
        buffer.writeShort(item.getTemplate().getType2());
        buffer.writeShort(item.getCustomType1());
        buffer.writeShort(item.isEquipped());
        buffer.writeInt(item.getTemplate().getBodyPart().getMask());
        buffer.writeShort(item.getEnchantLevel());
        buffer.writeShort(item.getCustomType2());
        if (item.isAugmented()) {
            buffer.writeInt(item.getAugmentation().getAugmentationId());
        } else {
            buffer.writeInt(0);
        }

        buffer.writeInt(item.getMana());
        buffer.writeInt(item.isTimeLimitedItem() ? (int) (item.getRemainingTime() / 1000) : -9999);
        buffer.writeShort(item.getAttackElementType());
        buffer.writeShort(item.getAttackElementPower());
        for (byte i = 0; i < 6; i++) {
            buffer.writeShort(item.getElementDefAttr(i));
        }

        // Enchant Effects
        for (int op : item.getEnchantOptions()) {
            buffer.writeShort(op);
        }
    }
}
