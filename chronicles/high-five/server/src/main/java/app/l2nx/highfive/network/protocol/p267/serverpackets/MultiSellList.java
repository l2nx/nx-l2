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

import static app.l2nx.highfive.data.xml.MultisellData.PAGE_SIZE;

import app.l2nx.highfive.mechanics.multisell.Entry;
import app.l2nx.highfive.mechanics.multisell.Ingredient;
import app.l2nx.highfive.mechanics.multisell.ListContainer;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class MultiSellList extends ServerPacket {
    private int size;
    private int index;
    private final ListContainer list;
    private final boolean finished;

    public MultiSellList(ListContainer list, int index) {
        this.list = list;
        this.index = index;
        size = list.getEntries().size() - index;
        if (size > PAGE_SIZE) {
            finished = false;
            size = PAGE_SIZE;
        } else {
            finished = true;
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.MULTI_SELL_LIST.writeId(this, buffer);
        buffer.writeInt(list.getListId()); // list id
        buffer.writeInt(1 + (index / PAGE_SIZE)); // page started from 1
        buffer.writeInt(finished); // finished
        buffer.writeInt(PAGE_SIZE); // size of pages
        buffer.writeInt(size); // list length
        Entry ent;
        while (size-- > 0) {
            ent = list.getEntries().get(index++);
            buffer.writeInt(ent.getEntryId());
            buffer.writeByte(ent.isStackable());
            buffer.writeShort(0); // C6
            buffer.writeInt(0); // C6
            buffer.writeInt(0); // T1
            buffer.writeShort(65534); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(0); // T1
            buffer.writeShort(ent.getProducts().size());
            buffer.writeShort(ent.getIngredients().size());
            for (Ingredient ing : ent.getProducts()) {
                if (ing.getTemplate() != null) {
                    buffer.writeInt(ing.getTemplate().getDisplayId());
                    buffer.writeInt(ing.getTemplate().getBodyPart().getMask());
                    buffer.writeShort(ing.getTemplate().getType2());
                } else {
                    buffer.writeInt(ing.getItemId());
                    buffer.writeInt(0);
                    buffer.writeShort(65535);
                }

                buffer.writeLong(ing.getItemCount());
                if (ing.getItemInfo() != null) {
                    buffer.writeShort(ing.getItemInfo().getEnchantLevel()); // enchant level
                    buffer.writeInt(ing.getItemInfo().getAugmentId()); // augment id
                    buffer.writeInt(0); // mana
                    buffer.writeShort(ing.getItemInfo().getElementId()); // attack element
                    buffer.writeShort(ing.getItemInfo().getElementPower()); // element power
                    buffer.writeShort(ing.getItemInfo().getElementals()[0]); // fire
                    buffer.writeShort(ing.getItemInfo().getElementals()[1]); // water
                    buffer.writeShort(ing.getItemInfo().getElementals()[2]); // wind
                    buffer.writeShort(ing.getItemInfo().getElementals()[3]); // earth
                    buffer.writeShort(ing.getItemInfo().getElementals()[4]); // holy
                    buffer.writeShort(ing.getItemInfo().getElementals()[5]); // dark
                } else {
                    buffer.writeShort(ing.getEnchantLevel()); // enchant level
                    buffer.writeInt(0); // augment id
                    buffer.writeInt(0); // mana
                    buffer.writeShort(0); // attack element
                    buffer.writeShort(0); // element power
                    buffer.writeShort(0); // fire
                    buffer.writeShort(0); // water
                    buffer.writeShort(0); // wind
                    buffer.writeShort(0); // earth
                    buffer.writeShort(0); // holy
                    buffer.writeShort(0); // dark
                }
            }

            for (Ingredient ing : ent.getIngredients()) {
                buffer.writeInt(ing.getTemplate() != null ? ing.getTemplate().getDisplayId() : ing.getItemId());
                buffer.writeShort(ing.getTemplate() != null ? ing.getTemplate().getType2() : 65535);
                buffer.writeLong(ing.getItemCount());
                if (ing.getItemInfo() != null) {
                    buffer.writeShort(ing.getItemInfo().getEnchantLevel()); // enchant level
                    buffer.writeInt(ing.getItemInfo().getAugmentId()); // augment id
                    buffer.writeInt(0); // mana
                    buffer.writeShort(ing.getItemInfo().getElementId()); // attack element
                    buffer.writeShort(ing.getItemInfo().getElementPower()); // element power
                    buffer.writeShort(ing.getItemInfo().getElementals()[0]); // fire
                    buffer.writeShort(ing.getItemInfo().getElementals()[1]); // water
                    buffer.writeShort(ing.getItemInfo().getElementals()[2]); // wind
                    buffer.writeShort(ing.getItemInfo().getElementals()[3]); // earth
                    buffer.writeShort(ing.getItemInfo().getElementals()[4]); // holy
                    buffer.writeShort(ing.getItemInfo().getElementals()[5]); // dark
                } else {
                    buffer.writeShort(ing.getEnchantLevel()); // enchant level
                    buffer.writeInt(0); // augment id
                    buffer.writeInt(0); // mana
                    buffer.writeShort(0); // attack element
                    buffer.writeShort(0); // element power
                    buffer.writeShort(0); // fire
                    buffer.writeShort(0); // water
                    buffer.writeShort(0); // wind
                    buffer.writeShort(0); // earth
                    buffer.writeShort(0); // holy
                    buffer.writeShort(0); // dark
                }
            }
        }
    }
}
