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
import app.l2nx.highfive.managers.IdManager;
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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * Items keyed by object id in insertion order, the order the client lists them in and replay relies on. Every walk
 * that calls out (destroys, saves, packets) runs over a copy, since the call may add or remove an item.
 *
 * <p>A pending item ({@link Item#isPending()}) does not exist for gameplay. Lookups by object or template id, the item
 * lists, the counts and the adena balance leave it out, so a destroy or move by template id takes a free instance and
 * a check before a mutation sees what the mutation can take. Handed a pending item anyway, {@code transferItem},
 * {@code destroyItem}, {@code dropItem} and {@code equipItem} refuse it with {@code null} or no effect.
 *
 * <p>What has to see the memory as held reads it internally or through the {@code ...IncludingPending} accessors:
 * the stackable merge of {@code addItem} and of a {@code transferItem} target (the commit keeps what a pending stack
 * took in meanwhile as a delta), the mail SPI that reserves, releases and applies, the size and weight, the adena
 * limit and the inventory window, which shows the item until the commit moves it. Whether a new item needs a slot is
 * answered from the gameplay view: an outgoing commit turns what merged into a pending stack into a new one. The
 * paperdoll keeps a worn pending stack (ammunition a mail adds to) with its stats. Saves skip a pending item, as
 * writing the pre-commit memory would undo the commit.
 * @author Advi
 */
@Slf4j
@SingleWriter
public abstract class ItemContainer {
    // An item's object id never changes while it sits in a container: only airships and monsters refresh theirs.
    private final Int2ObjectLinkedOpenHashMap<Item> byObjectId = new Int2ObjectLinkedOpenHashMap<>();
    private final ObjectCollection<Item> liveItems = ObjectCollections.unmodifiable(byObjectId.values());

    protected ItemContainer() {}

    protected abstract Creature getOwner();

    protected abstract ItemLocation getBaseLocation();

    /** For walks that call nothing able to add or remove an item; any other walk leases a snapshot of it. */
    protected final ObjectCollection<Item> liveItems() {
        return liveItems;
    }

    /** The gameplay view of {@link #liveItems()}, under the same walk rule: the items less the pending ones. */
    protected final Iterable<Item> freeItems() {
        return anyPending() ? items(_ -> true, true) : liveItems;
    }

    /**
     * The gameplay view of {@link #liveItems()}, under the same walk rule: the items {@code filter} accepts, less the
     * pending ones. The filter runs first, since the pending check costs two lookups.
     */
    protected final Iterable<Item> freeItems(Predicate<? super Item> filter) {
        return items(filter, anyPending());
    }

    // A value transaction in flight is rare: while none holds an item, no walk pays the per-item pending check.
    private static boolean anyPending() {
        return !WorldPartition.pendingItems().isEmpty()
                || !WorldPartition.awaitedStacks().isEmpty();
    }

    private Iterable<Item> items(Predicate<? super Item> filter, boolean skipPending) {
        return () -> new FilteredItems(liveItems.iterator(), filter, skipPending);
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
        final ObjectArrayList<Item> result = new ObjectArrayList<>(byObjectId.size());
        for (Item item : freeItems()) {
            result.add(item);
        }

        return result.isEmpty() ? ObjectLists.emptyList() : ObjectLists.unmodifiable(result);
    }

    public void collectItems(List<? super Item> into, Predicate<Item> filter) {
        WorldPartition.checkOwner();
        collect(into, filter, false);
    }

    public void collectItemsIncludingPending(List<? super Item> into, Predicate<Item> filter) {
        WorldPartition.checkOwner();
        collect(into, filter, true);
    }

    private void collect(List<? super Item> into, Predicate<Item> filter, boolean includePending) {
        final boolean skipPending = !includePending && anyPending();
        // The filter is the caller's code and may add or remove an item, so it runs over a copy.
        try (Snapshot<Item> snapshot = ScratchBuffers.snapshot(byObjectId.values())) {
            for (Item item : snapshot) {
                if (filter.test(item) && !(skipPending && item.isPending())) {
                    into.add(item);
                }
            }
        }
    }

    public Item getItemByItemId(int itemId) {
        WorldPartition.checkOwner();
        return findByItemId(itemId, null, false);
    }

    public Item getItemByItemIdIncludingPending(int itemId) {
        WorldPartition.checkOwner();
        return findByItemId(itemId, null, true);
    }

    public boolean haveItemForSelfResurrection() {
        WorldPartition.checkOwner();
        return freeItems(item -> item.getTemplate().isAllowSelfResurrection())
                .iterator()
                .hasNext();
    }

    public List<Item> getAllItemsByItemId(int itemId) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(item -> item.getId() == itemId)) {
            result.add(item);
        }

        return ObjectLists.unmodifiable(result);
    }

    public Item getItemByItemId(int itemId, Item itemToIgnore) {
        WorldPartition.checkOwner();
        return findByItemId(itemId, itemToIgnore, false);
    }

    private Item findByItemId(int itemId, Item itemToIgnore, boolean includePending) {
        final Predicate<Item> match = item -> (item.getId() == itemId) && !item.equals(itemToIgnore);
        final Iterator<Item> found =
                items(match, !includePending && anyPending()).iterator();
        return found.hasNext() ? found.next() : null;
    }

    public Item getItemByObjectId(int objectId) {
        WorldPartition.checkOwner();
        return free(byObjectId.get(objectId));
    }

    public Item getItemIncludingPending(int objectId) {
        WorldPartition.checkOwner();
        return byObjectId.get(objectId);
    }

    protected static Item free(Item item) {
        return (item == null) || item.isPending() ? null : item;
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
        for (Item item : freeItems(item -> (item.getId() == itemId)
                && ((item.getEnchantLevel() == enchantLevel) || (enchantLevel < 0))
                && (includeEquipped || !item.isEquipped()))) {
            if (item.isStackable()) {
                return item.getCount();
            }

            count++;
        }

        return count;
    }

    public Item addItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        Item newItem = item;
        final Item olditem = findByItemId(newItem.getId(), null, true);

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
        Item item = findByItemId(itemId, null, true);

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
     * @return the target's item, {@code null} when the count is not positive, the container does not hold the item, or
     *     the item is pending
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
        if ((sourceitem == null) || sourceitem.isPending()) {
            return null;
        }

        Item targetitem = sourceitem.isStackable() ? target.findByItemId(sourceitem.getId(), null, true) : null;

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
     *     holds the item, or the item is pending
     */
    public Item destroyItem(ItemProcessType process, Item item, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        // Checked before removeItem, whose overrides clean up the owner's state before removing.
        if ((count <= 0) || !byObjectId.containsKey(item.getObjectId()) || item.isPending()) {
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
        final Item item = findByItemId(itemId, null, false);
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
        final Item adena = findByItemId(Inventory.ADENA_ID, null, false);
        return adena == null ? 0 : adena.getCount();
    }

    /**
     * Puts in an item built from a row a value transaction committed to this container: the row is stored, so nothing
     * is written.
     */
    public void addCommitted(Item item) {
        WorldPartition.checkOwner();
        World.addObject(item);
        item.setLastChange(Item.ADDED);
        addItem(item);
        item.giveSkillsToOwner();
        refreshWeight();
    }

    /**
     * Changes the count of an item as a committed value transaction already changed its row; nothing is written.
     *
     * @return {@code false} when the container does not hold the item or nothing of it would be left
     */
    public boolean changeCommitted(Item item, long delta) {
        WorldPartition.checkOwner();
        if (!byObjectId.containsKey(item.getObjectId()) || ((item.getCount() + delta) <= 0)) {
            return false;
        }

        item.changeCount(ItemProcessType.TRANSFER, delta, null, null);
        item.setLastChange(Item.MODIFIED);
        refreshWeight();
        return true;
    }

    /**
     * Takes an item out of memory after a committed value transaction moved its row away or deleted it; nothing is
     * written.
     *
     * @return {@code false} when the container does not hold the item
     */
    public boolean removeCommitted(Item item) {
        WorldPartition.checkOwner();
        if (!byObjectId.containsKey(item.getObjectId())) {
            return false;
        }

        removeItem(item);
        item.removeSkillsFromOwner();
        item.setLastChange(Item.REMOVED);
        item.stopAllTasks();
        World.removeObject(item);
        IdManager.getInstance().releaseId(item.getObjectId());
        refreshWeight();
        return true;
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

                if (item.isStackable() && (findByItemId(item.getId(), null, true) != null)) {
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

    private static final class FilteredItems implements Iterator<Item> {
        private final Iterator<Item> items;
        private final Predicate<? super Item> filter;
        private final boolean skipPending;
        private Item next;

        FilteredItems(Iterator<Item> items, Predicate<? super Item> filter, boolean skipPending) {
            this.items = items;
            this.filter = filter;
            this.skipPending = skipPending;
        }

        @Override
        public boolean hasNext() {
            while ((next == null) && items.hasNext()) {
                final Item item = items.next();
                if (filter.test(item) && !(skipPending && item.isPending())) {
                    next = item;
                }
            }

            return next != null;
        }

        @Override
        public Item next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            final Item item = next;
            next = null;
            return item;
        }
    }
}
