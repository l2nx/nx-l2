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
package app.l2nx.highfive.entity.itemcontainer;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class PetInventory extends Inventory {
    private final Pet owner;

    public PetInventory(Pet owner) {
        this.owner = owner;
    }

    @Override
    public Pet getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    /** The items belong to the pet's master, so they are stored under the master's id. */
    @Override
    public int getOwnerId() {
        WorldPartition.checkOwner();
        final Player master = owner.getOwner();
        return master == null ? 0 : master.getObjectId();
    }

    @Override
    protected void refreshWeight() {
        super.refreshWeight();
        owner.updateAndBroadcastStatus(1);
    }

    public boolean validateCapacity(Item item) {
        WorldPartition.checkOwner();
        int slots = 0;
        if ((!item.isStackable() || (getItemByItemId(item.getId()) == null))
                && !item.getTemplate().hasExImmediateEffect()) {
            slots++;
        }

        return validateCapacity(slots);
    }

    @Override
    public boolean validateCapacity(long slots) {
        WorldPartition.checkOwner();
        return (liveItems().size() + slots) <= owner.getInventoryLimit();
    }

    public boolean validateWeight(Item item, long count) {
        WorldPartition.checkOwner();
        int weight = 0;
        final ItemTemplate template = ItemData.getInstance().getTemplate(item.getId());
        if (template == null) {
            return false;
        }

        weight += count * template.getWeight();
        return validateWeight(weight);
    }

    @Override
    public boolean validateWeight(long weight) {
        WorldPartition.checkOwner();
        return (totalWeight + weight) <= owner.getMaxLoad();
    }

    @Override
    protected ItemLocation getBaseLocation() {
        return ItemLocation.PET;
    }

    @Override
    protected ItemLocation getEquipLocation() {
        return ItemLocation.PET_EQUIP;
    }

    @Override
    public void restore() {
        WorldPartition.checkOwner();
        super.restore();

        try (Snapshot<Item> items = ScratchBuffers.snapshot(liveItems())) {
            for (Item item : items) {
                if (item.isEquipped() && !item.getTemplate().checkCondition(owner, owner, false)) {
                    unEquipItemInSlot(item.getLocationSlot());
                }
            }
        }
    }

    public void transferItemsToOwner() {
        WorldPartition.checkOwner();
        final Player master = owner.getOwner();
        try (Snapshot<Item> items = ScratchBuffers.empty()) {
            for (Item item : freeItems()) {
                items.add(item);
            }

            for (Item item : items) {
                owner.transferItem(
                        ItemProcessType.TRANSFER,
                        item.getObjectId(),
                        item.getCount(),
                        master.getInventory(),
                        master,
                        owner);
            }
        }
    }
}
