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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.mechanics.variables.ItemVariables;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.model.ItemRow;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectCollections;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * Items keyed by object id in insertion order, the order the client lists them in and replay relies on. Every walk
 * that calls out (destroys, saves, packets) runs over a copy, since the call may add or remove an item.
 * @author Advi
 */
@Slf4j
@SingleWriter
public abstract class ItemContainer {
    // An item's object id never changes while it sits in a container: only airships and monsters refresh theirs.
    private final Int2ObjectLinkedOpenHashMap<Item> byObjectId = new Int2ObjectLinkedOpenHashMap<>();
    private final ObjectCollection<Item> liveItems = ObjectCollections.unmodifiable(byObjectId.values());

    // TODO: remove with Mail (#150), the last subclass that walks this field itself; its walks transfer items out of
    // the container, so the view iterates a copy.
    protected final Collection<Item> items = new AbstractCollection<>() {
        @Override
        public Iterator<Item> iterator() {
            return List.copyOf(byObjectId.values()).iterator();
        }

        @Override
        public int size() {
            return byObjectId.size();
        }

        @Override
        public void clear() {
            byObjectId.clear();
        }
    };

    protected ItemContainer() {}

    protected abstract Creature getOwner();

    protected abstract ItemLocation getBaseLocation();

    /** For walks that call nothing able to add or remove an item; any other walk leases a snapshot of it. */
    protected final ObjectCollection<Item> liveItems() {
        return liveItems;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return "ItemContainer";
    }

    public int getOwnerId() {
        WorldPartition.checkOwner();
        final Creature owner = getOwner();
        return owner == null ? 0 : owner.getObjectId();
    }

    public int getSize() {
        WorldPartition.checkOwner();
        return byObjectId.size();
    }

    public List<Item> getItems() {
        WorldPartition.checkOwner();
        return byObjectId.isEmpty()
                ? ObjectLists.emptyList()
                : ObjectLists.unmodifiable(new ObjectArrayList<>(byObjectId.values()));
    }

    public void collectItems(List<? super Item> into, Predicate<Item> filter) {
        WorldPartition.checkOwner();
        // The filter is the caller's code and may add or remove an item, so it runs over a copy.
        try (Snapshot<Item> snapshot = ScratchBuffers.snapshot(byObjectId.values())) {
            for (Item item : snapshot) {
                if (filter.test(item)) {
                    into.add(item);
                }
            }
        }
    }

    public Item getItemByItemId(int itemId) {
        WorldPartition.checkOwner();
        return findByItemId(itemId, null);
    }

    public boolean haveItemForSelfResurrection() {
        WorldPartition.checkOwner();
        for (Item item : byObjectId.values()) {
            if (item.getTemplate().isAllowSelfResurrection()) {
                return true;
            }
        }

        return false;
    }

    public List<Item> getAllItemsByItemId(int itemId) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : byObjectId.values()) {
            if (itemId == item.getId()) {
                result.add(item);
            }
        }

        return ObjectLists.unmodifiable(result);
    }

    public Item getItemByItemId(int itemId, Item itemToIgnore) {
        WorldPartition.checkOwner();
        return findByItemId(itemId, itemToIgnore);
    }

    private Item findByItemId(int itemId, Item itemToIgnore) {
        for (Item item : byObjectId.values()) {
            if ((item.getId() == itemId) && !item.equals(itemToIgnore)) {
                return item;
            }
        }

        return null;
    }

    public Item getItemByObjectId(int objectId) {
        WorldPartition.checkOwner();
        return byObjectId.get(objectId);
    }

    /**
     * @param enchantLevel the item enchant level, use -1 to match any enchant level
     */
    public long getInventoryItemCount(int itemId, int enchantLevel) {
        WorldPartition.checkOwner();
        return countItems(itemId, enchantLevel, true);
    }

    /**
     * @param enchantLevel the item enchant level, use -1 to match any enchant level
     */
    public long getInventoryItemCount(int itemId, int enchantLevel, boolean includeEquipped) {
        WorldPartition.checkOwner();
        return countItems(itemId, enchantLevel, includeEquipped);
    }

    private long countItems(int itemId, int enchantLevel, boolean includeEquipped) {
        long count = 0;
        for (Item item : byObjectId.values()) {
            if ((item.getId() == itemId)
                    && ((item.getEnchantLevel() == enchantLevel) || (enchantLevel < 0))
                    && (includeEquipped || !item.isEquipped())) {
                if (item.isStackable()) {
                    return item.getCount();
                }

                count++;
            }
        }

        return count;
    }

    public Item addItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        Item newItem = item;
        final Item olditem = findByItemId(newItem.getId(), null);

        if ((olditem != null) && olditem.isStackable()) {
            final long count = newItem.getCount();
            olditem.changeCount(process, count, actor, reference);
            olditem.setLastChange(Item.MODIFIED);

            ItemManager.destroyItem(process, newItem, actor, reference);
            newItem.updateDatabase();
            newItem = olditem;
        } else {
            newItem.setOwnerId(process, getOwnerId(), actor, reference);
            newItem.setItemLocation(getBaseLocation());
            newItem.setLastChange((Item.ADDED));

            addItem(newItem);
        }

        refreshWeight();
        return newItem;
    }

    public Item addItem(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        Item item = findByItemId(itemId, null);

        if ((item != null) && item.isStackable()) {
            item.changeCount(process, count, actor, reference);
            item.setLastChange(Item.MODIFIED);
        } else {
            final InventoryUpdate iu = new InventoryUpdate();
            for (int i = 0; i < count; i++) {
                final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
                if (template == null) {
                    log.warn("Invalid ItemId ({}) requested by {}", itemId, (actor != null ? actor : process));
                    return null;
                }

                item = ItemManager.createItem(process, itemId, template.isStackable() ? count : 1, actor, reference);
                item.setOwnerId(getOwnerId());
                item.setItemLocation(getBaseLocation());
                item.setLastChange(Item.ADDED);
                item.setEnchantLevel(template.getDefaultEnchantLevel());

                addItem(item);

                if ((count > 1) && (i < (count - 1))) {
                    iu.addNewItem(item);
                }

                // A stackable item holds the whole count in one instance.
                if (template.isStackable() || !GeneralConfig.MULTIPLE_ITEM_DROP) {
                    break;
                }
            }

            if ((count > 1)
                    && (item != null)
                    && !item.isStackable()
                    && (item.getItemLocation() == ItemLocation.INVENTORY)) {
                actor.sendInventoryUpdate(iu);
            }
        }

        refreshWeight();
        return item;
    }

    /**
     * @param countValue Quantity of items to be transfered, capped at the count present
     * @return the target's item, {@code null} when the count is not positive or the container does not hold the item
     */
    public Item transferItem(
            ItemProcessType process,
            int objectId,
            long countValue,
            ItemContainer target,
            Player actor,
            Object reference) {
        WorldPartition.checkOwner();
        if ((target == null) || (countValue <= 0)) {
            return null;
        }

        final Item sourceitem = byObjectId.get(objectId);
        if (sourceitem == null) {
            return null;
        }

        Item targetitem = sourceitem.isStackable() ? target.getItemByItemId(sourceitem.getId()) : null;
        final long count = Math.min(countValue, sourceitem.getCount());

        if ((sourceitem.getCount() == count) && (targetitem == null) && !sourceitem.isStackable()) {
            removeItem(sourceitem);
            target.addItem(process, sourceitem, actor, reference);
            targetitem = sourceitem;
        } else {
            if (sourceitem.getCount() > count) {
                sourceitem.changeCount(process, -count, actor, reference);
            } else {
                removeItem(sourceitem);
                ItemManager.destroyItem(process, sourceitem, actor, reference);
            }

            if (targetitem != null) {
                targetitem.changeCount(process, count, actor, reference);
            } else {
                targetitem = target.addItem(process, sourceitem.getId(), count, actor, reference);
            }
        }

        sourceitem.updateDatabase(true);
        if ((targetitem != sourceitem) && (targetitem != null)) {
            targetitem.updateDatabase();
        }

        if (sourceitem.isAugmented()) {
            sourceitem.getAugmentation().removeBonus(actor);
        }

        refreshWeight();
        target.refreshWeight();
        return targetitem;
    }

    public Item destroyItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        return destroyItem(process, item, item.getCount(), actor, reference);
    }

    /**
     * @return {@code null} when the count is not positive, the container holds fewer than {@code count} or no longer
     *     holds the item
     */
    public Item destroyItem(ItemProcessType process, Item item, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        // Checked before removeItem, whose overrides clean up the owner's state before removing.
        if ((count <= 0) || !byObjectId.containsKey(item.getObjectId())) {
            return null;
        }

        if (item.getCount() > count) {
            item.changeCount(process, -count, actor, reference);
            item.setLastChange(Item.MODIFIED);
            refreshWeight();
            return item;
        }

        if (item.getCount() < count) {
            return null;
        }

        removeItem(item);
        ItemManager.destroyItem(process, item, actor, reference);
        item.updateDatabase();
        refreshWeight();
        item.stopAllTasks();
        return item;
    }

    public Item destroyItem(ItemProcessType process, int objectId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = byObjectId.get(objectId);
        return item == null ? null : destroyItem(process, item, count, actor, reference);
    }

    public Item destroyItemByItemId(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = findByItemId(itemId, null);
        return item == null ? null : destroyItem(process, item, count, actor, reference);
    }

    public void destroyAllItems(ItemProcessType process, Player actor, Object reference) {
        WorldPartition.checkOwner();
        try (Snapshot<Item> snapshot = ScratchBuffers.snapshot(byObjectId.values())) {
            for (Item item : snapshot) {
                destroyItem(process, item, actor, reference);
            }
        }
    }

    public long getAdena() {
        WorldPartition.checkOwner();
        final Item adena = findByItemId(Inventory.ADENA_ID, null);
        return adena == null ? 0 : adena.getCount();
    }

    protected void addItem(Item item) {
        byObjectId.put(item.getObjectId(), item);
    }

    protected boolean removeItem(Item item) {
        return byObjectId.remove(item.getObjectId()) != null;
    }

    protected final boolean holds(Item item) {
        return byObjectId.containsKey(item.getObjectId());
    }

    protected void clearItems() {
        byObjectId.clear();
    }

    protected void refreshWeight() {}

    public void deleteMe() {
        WorldPartition.checkOwner();
        try (Snapshot<Item> snapshot = ScratchBuffers.snapshot(byObjectId.values())) {
            if ((this instanceof PlayerInventory) || (this instanceof PlayerWarehouse) || (getOwner() != null)) {
                for (Item item : snapshot) {
                    item.updateDatabase(true);
                    item.stopAllTasks();

                    final ItemVariables vars = item.getScript(ItemVariables.class);
                    if (vars != null) {
                        vars.saveNow();
                    }
                }
            }

            for (Item item : snapshot) {
                World.removeObject(item);
            }
        }

        clearItems();
    }

    public void updateDatabase() {
        WorldPartition.checkOwner();
        if (getOwner() != null) {
            try (Snapshot<Item> snapshot = ScratchBuffers.snapshot(byObjectId.values())) {
                for (Item item : snapshot) {
                    item.updateDatabase(true);
                }
            }
        }
    }

    public void restore() {
        WorldPartition.checkOwner();
        try {
            for (ItemRow row : ItemManager.getInstance().findItems(getOwnerId(), getBaseLocation())) {
                final Item item = Item.restoreFromDb(getOwnerId(), row);
                if (item == null) {
                    continue;
                }

                World.addObject(item);

                final Player owner = getOwner() != null ? getOwner().asPlayer() : null;

                if (item.isStackable() && (findByItemId(item.getId(), null) != null)) {
                    addItem(ItemProcessType.RESTORE, item, owner, null);
                } else {
                    addItem(item);
                }
            }

            refreshWeight();
        } catch (DataAccessException e) {
            log.warn("Could not restore container:", e);
        }
    }

    public boolean validateCapacity(long slots) {
        WorldPartition.checkOwner();
        return true;
    }

    public boolean validateWeight(long weight) {
        WorldPartition.checkOwner();
        return true;
    }

    public boolean validateCapacityByItemId(int itemId, long count) {
        WorldPartition.checkOwner();
        final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
        return (template == null) || (template.isStackable() ? validateCapacity(1) : validateCapacity(count));
    }

    public boolean validateWeightByItemId(int itemId, long count) {
        WorldPartition.checkOwner();
        final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
        return (template == null) || validateWeight(template.getWeight() * count);
    }
}
