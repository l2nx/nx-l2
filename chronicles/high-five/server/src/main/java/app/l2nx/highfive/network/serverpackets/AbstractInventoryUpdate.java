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

import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public abstract class AbstractInventoryUpdate extends AbstractItemPacket {
    @Getter
    private final Map<Integer, ItemInfo> itemEntries = new HashMap<>();

    public AbstractInventoryUpdate() {}

    public AbstractInventoryUpdate(Item item) {
        addItem(item);
    }

    public AbstractInventoryUpdate(List<ItemInfo> items) {
        synchronized (itemEntries) {
            for (ItemInfo item : items) {
                itemEntries.put(item.getObjectId(), item);
            }
        }
    }

    public void addItem(Item item) {
        synchronized (itemEntries) {
            itemEntries.put(item.getObjectId(), new ItemInfo(item));
        }
    }

    public void addNewItem(Item item) {
        synchronized (itemEntries) {
            itemEntries.put(item.getObjectId(), new ItemInfo(item, 1));
        }
    }

    public void addModifiedItem(Item item) {
        synchronized (itemEntries) {
            itemEntries.put(item.getObjectId(), new ItemInfo(item, 2));
        }
    }

    public void addRemovedItem(Item item) {
        synchronized (itemEntries) {
            itemEntries.put(item.getObjectId(), new ItemInfo(item, 3));
        }
    }

    public void addItems(Collection<Item> items) {
        synchronized (itemEntries) {
            for (Item item : items) {
                itemEntries.put(item.getObjectId(), new ItemInfo(item));
            }
        }
    }

    public void putAll(Map<Integer, ItemInfo> items) {
        synchronized (itemEntries) {
            itemEntries.putAll(items);
        }
    }

    public Collection<ItemInfo> getItems() {
        return itemEntries.values();
    }

    protected void writeItems(WriteBuffer buffer) {
        synchronized (itemEntries) {
            buffer.writeShort(itemEntries.size());
            for (ItemInfo item : itemEntries.values()) {
                buffer.writeShort(item.getChange()); // Update type : 01-add, 02-modify, 03-remove
                writeItem(item, buffer);
            }

            itemEntries.clear();
        }
    }
}
