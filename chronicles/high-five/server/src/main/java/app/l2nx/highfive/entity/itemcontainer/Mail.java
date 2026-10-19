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
package app.l2nx.highfive.entity.itemcontainer;

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author DS
 */
@Slf4j
public class Mail extends ItemContainer {
    @Getter
    private final int ownerId;

    @Getter
    private int messageId;

    public Mail(int objectId, int messageId) {
        ownerId = objectId;
        this.messageId = messageId;
    }

    @Override
    public String getName() {
        return "Mail";
    }

    @Override
    public Player getOwner() {
        return null;
    }

    @Override
    public ItemLocation getBaseLocation() {
        return ItemLocation.MAIL;
    }

    public void setNewMessageId(int messageId) {
        this.messageId = messageId;
        for (Item item : items) {
            if (item == null) {
                continue;
            }

            item.setItemLocation(getBaseLocation(), messageId);
        }

        updateDatabase();
    }

    public void returnToWh(ItemContainer wh) {
        for (Item item : items) {
            if (item == null) {
                continue;
            }

            if (wh != null) {
                transferItem(ItemProcessType.TRANSFER, item.getObjectId(), item.getCount(), wh, null, null);
            } else {
                item.setItemLocation(ItemLocation.WAREHOUSE);
            }
        }
    }

    @Override
    protected void addItem(Item item) {
        super.addItem(item);
        item.setItemLocation(getBaseLocation(), messageId);
        item.updateDatabase(true);
    }

    @Override
    public void updateDatabase() {
        items.forEach(i -> i.updateDatabase(true));
    }

    @Override
    public void restore() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT object_id, item_id, count, enchant_level, loc, loc_data, custom_type1, custom_type2, mana_left, time FROM items WHERE owner_id=? AND loc=? AND loc_data=?")) {
            ps.setInt(1, ownerId);
            ps.setString(2, getBaseLocation().name());
            ps.setInt(3, messageId);
            try (ResultSet inv = ps.executeQuery()) {
                Item item;
                while (inv.next()) {
                    item = Item.restoreFromDb(ownerId, inv);
                    if (item == null) {
                        continue;
                    }

                    World.addObject(item);

                    // If stackable item is found just add to current quantity.
                    if (item.isStackable() && (getItemByItemId(item.getId()) != null)) {
                        addItem(ItemProcessType.RESTORE, item, null, null);
                    } else {
                        addItem(item);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("could not restore container:", e);
        }
    }

    @Override
    public void deleteMe() {
        items.forEach(item -> {
            item.updateDatabase(true);
            item.stopAllTasks();
            World.removeObject(item);
        });
        items.clear();
    }
}
