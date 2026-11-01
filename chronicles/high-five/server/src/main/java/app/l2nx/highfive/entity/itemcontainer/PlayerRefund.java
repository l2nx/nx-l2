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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.partition.WorldPartition;
import lombok.extern.slf4j.Slf4j;

/**
 * The items last sold to a merchant, oldest first; past the limit the oldest one is destroyed.
 * @author DS
 */
@Slf4j
@SingleWriter
public class PlayerRefund extends ItemContainer {
    private static final int LIMIT = 12;

    private final Player owner;

    public PlayerRefund(Player owner) {
        this.owner = owner;
    }

    @Override
    public Player getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    @Override
    public String getName() {
        WorldPartition.checkOwner();
        return "Refund";
    }

    @Override
    public ItemLocation getBaseLocation() {
        WorldPartition.checkOwner();
        return ItemLocation.REFUND;
    }

    @Override
    protected void addItem(Item item) {
        super.addItem(item);
        try {
            if (liveItems().size() > LIMIT) {
                final Item removedItem = liveItems().iterator().next();
                removeItem(removedItem);
                ItemManager.destroyItem(ItemProcessType.REFUND, removedItem, owner, null);
                removedItem.updateDatabase(true);
            }
        } catch (Exception e) {
            log.error("addItem()", e);
        }
    }

    @Override
    public void refreshWeight() {
        WorldPartition.checkOwner();
    }

    @Override
    public void deleteMe() {
        WorldPartition.checkOwner();
        try (Snapshot<Item> items = ScratchBuffers.snapshot(liveItems())) {
            for (Item item : items) {
                try {
                    removeItem(item);
                    ItemManager.destroyItem(ItemProcessType.REFUND, item, owner, null);
                    item.updateDatabase(true);
                } catch (RuntimeException e) {
                    log.error("Could not destroy refund item {}", item.getObjectId(), e);
                    // A failed destroy may stop before the registry removal; the item must not outlive the refund.
                    World.removeObject(item);
                }
            }
        }

        // A refund never outlives its owner's session: items a failed destroy left behind go too.
        clearItems();
    }

    @Override
    public void restore() {
        WorldPartition.checkOwner();
    }
}
