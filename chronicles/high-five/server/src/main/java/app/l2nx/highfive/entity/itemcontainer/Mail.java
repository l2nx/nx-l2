/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.repository.item.model.ItemRow;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

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

    /**
     * Moves the attachments into a warehouse: the given one when its owner is online, else straight into the stored
     * warehouse of {@code ownerId}.
     */
    public void returnToWh(ItemContainer wh, int ownerId) {
        for (Item item : items) {
            if (item == null) {
                continue;
            }

            if (wh != null) {
                transferItem(ItemProcessType.TRANSFER, item.getObjectId(), item.getCount(), wh, null, null);
            } else {
                item.setOwnerId(ownerId);
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
        try {
            for (ItemRow row : ItemManager.getInstance().findAttachments(messageId)) {
                final Item item = Item.restoreFromDb(ownerId, row);
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
        } catch (DataAccessException e) {
            log.warn("could not restore container:", e);
        }
    }

    /**
     * Removes the attachments from the world without writing them: their rows were already moved or deleted.
     */
    public void discard() {
        items.forEach(item -> {
            item.stopAllTasks();
            World.removeObject(item);
        });
        items.clear();
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
