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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Kerberos
 */
public class ExChooseInventoryAttributeItem extends ServerPacket {
    private final int itemId;
    private final byte atribute;
    private final int level;

    public ExChooseInventoryAttributeItem(Item item) {
        itemId = item.getDisplayId();
        atribute = Elementals.getItemElement(itemId);
        if (atribute == Elementals.NONE) {
            throw new IllegalArgumentException("Undefined Atribute item: " + item);
        }

        level = Elementals.getMaxElementLevel(itemId);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_CHOOSE_INVENTORY_ATTRIBUTE_ITEM.writeId(this, buffer);
        buffer.writeInt(itemId);

        // Structure for now
        // Must be 0x01 for stone/crystal attribute type
        buffer.writeInt(atribute == Elementals.FIRE); // Fire
        buffer.writeInt(atribute == Elementals.WATER); // Water
        buffer.writeInt(atribute == Elementals.WIND); // Wind
        buffer.writeInt(atribute == Elementals.EARTH); // Earth
        buffer.writeInt(atribute == Elementals.HOLY); // Holy
        buffer.writeInt(atribute == Elementals.DARK); // Unholy
        buffer.writeInt(level); // Item max attribute level
    }
}
