/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemAdd;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemDestroy;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemDrop;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemTransfer;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.TradeItem;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.model.StoredItem;
import app.l2nx.highfive.util.ArrayUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class PlayerInventory extends Inventory {

    private final Player owner;

    private Item adenaInstance;

    private Item ancientAdenaInstance;

    private int questItemSize;

    private int[] blockItems = null;

    /** -1 no block; 0 blocks the items in {@link #blockItems}; 1 allows only those items. */
    private int blockMode = -1;

    public PlayerInventory(Player owner) {
        this.owner = owner;
    }

    @Override
    public Player getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    public Item getAdenaInstance() {
        WorldPartition.checkOwner();
        return free(adenaInstance);
    }

    public Item getAdenaInstanceIncludingPending() {
        WorldPartition.checkOwner();
        return adenaInstance;
    }

    public Item getAncientAdenaInstance() {
        WorldPartition.checkOwner();
        return free(ancientAdenaInstance);
    }

    public Item getAncientAdenaInstanceIncludingPending() {
        WorldPartition.checkOwner();
        return ancientAdenaInstance;
    }

    public int getBlockItemCount() {
        WorldPartition.checkOwner();
        return blockItems == null ? 0 : blockItems.length;
    }

    public int getBlockItem(int index) {
        WorldPartition.checkOwner();
        return blockItems[index];
    }

    public int getBlockMode() {
        WorldPartition.checkOwner();
        return blockMode;
    }

    @Override
    protected ItemLocation getBaseLocation() {
        return ItemLocation.INVENTORY;
    }

    @Override
    protected ItemLocation getEquipLocation() {
        return ItemLocation.PAPERDOLL;
    }

    @Override
    public long getAdena() {
        WorldPartition.checkOwner();
        final Item adena = free(adenaInstance);
        return adena != null ? adena.getCount() : 0;
    }

    public long getAdenaIncludingPending() {
        WorldPartition.checkOwner();
        return adenaInstance != null ? adenaInstance.getCount() : 0;
    }

    public long getAncientAdena() {
        WorldPartition.checkOwner();
        final Item ancientAdena = free(ancientAdenaInstance);
        return (ancientAdena != null) ? ancientAdena.getCount() : 0;
    }

    /**
     * @return read-only, in inventory order
     */
    public List<Item> getUniqueItems(
            boolean allowAdena, boolean allowAncientAdena, boolean onlyAvailable, boolean checkSellable) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(item -> (allowAdena || (item.getId() != ADENA_ID))
                && (allowAncientAdena || (item.getId() != ANCIENT_ADENA_ID)))) {
            boolean isDuplicate = false;
            for (Item addedItem : result) {
                if (addedItem.getId() == item.getId()) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate
                    && (!onlyAvailable
                            || ((!checkSellable || item.isSellable()) && item.isAvailable(owner, false, false)))) {
                result.add(item);
            }
        }

        return ObjectLists.unmodifiable(result);
    }

    /**
     * @return read-only, in inventory order
     */
    public List<Item> getUniqueItemsByEnchantLevel(
            boolean allowAdena, boolean allowAncientAdena, boolean onlyAvailable) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(item -> (allowAdena || (item.getId() != ADENA_ID))
                && (allowAncientAdena || (item.getId() != ANCIENT_ADENA_ID)))) {
            boolean isDuplicate = false;
            for (Item addedItem : result) {
                if ((addedItem.getId() == item.getId()) && (addedItem.getEnchantLevel() == item.getEnchantLevel())) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate && (!onlyAvailable || (item.isSellable() && item.isAvailable(owner, false, false)))) {
                result.add(item);
            }
        }

        return ObjectLists.unmodifiable(result);
    }

    /**
     * @return read-only, in inventory order
     */
    public List<Item> getAllItemsByItemId(int itemId, boolean includeEquipped) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(item -> (itemId == item.getId()) && (includeEquipped || !item.isEquipped()))) {
            result.add(item);
        }

        return ObjectLists.unmodifiable(result);
    }

    public List<Item> getAllItemsByItemId(int itemId, int enchantment) {
        WorldPartition.checkOwner();
        return getAllItemsByItemId(itemId, enchantment, true);
    }

    /**
     * @return read-only, in inventory order
     */
    public List<Item> getAllItemsByItemId(int itemId, int enchantment, boolean includeEquipped) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(item -> (itemId == item.getId())
                && (item.getEnchantLevel() == enchantment)
                && (includeEquipped || !item.isEquipped()))) {
            result.add(item);
        }

        return ObjectLists.unmodifiable(result);
    }

    /**
     * Plans an all-or-nothing take into {@code plan}, unequipped instances first; a negative {@code count} takes
     * everything. Returns the shortfall and leaves the plan untouched, {@code 0} once planned.
     */
    public long planTake(int itemId, long count, boolean includeEquipped, Reference2LongMap<Item> plan) {
        WorldPartition.checkOwner();
        long available = 0;
        for (Item item : freeItems(item -> (item.getId() == itemId) && (includeEquipped || !item.isEquipped()))) {
            available += item.getCount() - plan.getLong(item);
        }

        if ((count >= 0) && (available < count)) {
            return count - available;
        }

        final long left = planFrom(itemId, false, count < 0 ? available : count, plan);
        if (left > 0) {
            planFrom(itemId, true, left, plan);
        }

        return 0;
    }

    private long planFrom(int itemId, boolean equipped, long count, Reference2LongMap<Item> plan) {
        long left = count;
        for (Item item : freeItems(item -> (item.getId() == itemId) && (item.isEquipped() == equipped))) {
            if (left == 0) {
                break;
            }

            final long planned = plan.getLong(item);
            final long take = Math.min(left, item.getCount() - planned);
            if (take > 0) {
                plan.put(item, planned + take);
                left -= take;
            }
        }

        return left;
    }

    /**
     * @return the items available for a transaction, read-only, in inventory order
     */
    public List<Item> getAvailableItems(boolean allowAdena, boolean allowNonTradeable, boolean feightable) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(item ->
                item.isAvailable(owner, allowAdena, allowNonTradeable) && canManipulateWithItemId(item.getId()))) {
            if (feightable) {
                if ((item.getItemLocation() == ItemLocation.INVENTORY) && item.isFreightable()) {
                    result.add(item);
                }
                continue;
            }

            result.add(item);
        }

        return ObjectLists.unmodifiable(result);
    }

    public List<Item> getAugmentedItems() {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(Item::isAugmented)) {
            result.add(item);
        }

        return ObjectLists.unmodifiable(result);
    }

    public List<Item> getElementItems() {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> result = new ObjectArrayList<>();
        for (Item item : freeItems(Item::hasElementals)) {
            result.add(item);
        }

        return ObjectLists.unmodifiable(result);
    }

    /**
     * @return the items available for a transaction adjusted by the trade list, read-only, in inventory order
     */
    public List<TradeItem> getAvailableItems(TradeList tradeList) {
        WorldPartition.checkOwner();
        final ObjectArrayList<TradeItem> result = new ObjectArrayList<>();
        for (Item item : freeItems(item -> item.isAvailable(owner, false, false))) {
            final TradeItem adjItem = tradeList.adjustAvailableItem(item);
            if (adjItem != null) {
                result.add(adjItem);
            }
        }

        return ObjectLists.unmodifiable(result);
    }

    public void adjustAvailableItem(TradeItem item) {
        WorldPartition.checkOwner();
        final int itemId = item.getItem().getId();
        boolean notAllEquipped = false;
        for (Item adjItem : freeItems(adjItem -> adjItem.getId() == itemId)) {
            if (!adjItem.isEquipable()) {
                notAllEquipped = true;
                break;
            }

            notAllEquipped |= !adjItem.isEquipped();
        }

        if (notAllEquipped) {
            final Item adjItem = getItemByItemId(itemId);
            item.setObjectId(adjItem.getObjectId());
            item.setEnchant(adjItem.getEnchantLevel());

            if (adjItem.getCount() < item.getCount()) {
                item.setCount(adjItem.getCount());
            }

            return;
        }

        item.setCount(0);
    }

    public void addAdena(ItemProcessType process, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        if (count > 0) {
            addItem(process, ADENA_ID, count, actor, reference);
        }
    }

    public boolean reduceAdena(ItemProcessType process, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        if (count > 0) {
            return destroyItemByItemId(process, ADENA_ID, count, actor, reference) != null;
        }

        return false;
    }

    public void addAncientAdena(ItemProcessType process, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        if (count > 0) {
            addItem(process, ANCIENT_ADENA_ID, count, actor, reference);
        }
    }

    public boolean reduceAncientAdena(ItemProcessType process, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        return (count > 0) && (destroyItemByItemId(process, ANCIENT_ADENA_ID, count, actor, reference) != null);
    }

    @Override
    public Item addItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item addedItem = super.addItem(process, item, actor, reference);
        if (addedItem != null) {
            if ((addedItem.getId() == ADENA_ID) && !addedItem.equals(adenaInstance)) {
                adenaInstance = addedItem;
            } else if ((addedItem.getId() == ANCIENT_ADENA_ID) && !addedItem.equals(ancientAdenaInstance)) {
                ancientAdenaInstance = addedItem;
            }

            if ((actor != null) && EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_ADD, actor)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnPlayerItemAdd(actor, addedItem), actor);
            }
        }

        return addedItem;
    }

    @Override
    public Item addItem(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = super.addItem(process, itemId, count, actor, reference);
        if (item != null) {
            if ((item.getId() == ADENA_ID) && !item.equals(adenaInstance)) {
                adenaInstance = item;
            } else if ((item.getId() == ANCIENT_ADENA_ID) && !item.equals(ancientAdenaInstance)) {
                ancientAdenaInstance = item;
            }

            if (actor != null) {
                final InventoryUpdate playerIU = new InventoryUpdate();
                if (item.isStackable() && (item.getCount() > count)) {
                    playerIU.addModifiedItem(item);
                } else {
                    playerIU.addNewItem(item);
                }

                actor.sendInventoryUpdate(playerIU);

                final StatusUpdate su = new StatusUpdate(actor);
                su.addAttribute(StatusUpdate.CUR_LOAD, actor.getCurrentLoad());
                actor.sendPacket(su);

                if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_ADD, actor)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnPlayerItemAdd(actor, item), actor);
                }
            }
        }

        return item;
    }

    @Override
    public Item transferItem(
            ItemProcessType process, int objectId, long count, ItemContainer target, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = super.transferItem(process, objectId, count, target, actor, reference);
        forgetGoneCurrencies();

        if ((item != null)
                && EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_TRANSFER, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerItemTransfer.builder()
                                    .player(actor)
                                    .item(item)
                                    .container(target)
                                    .build(),
                            item.getTemplate());
        }

        return item;
    }

    private void forgetGoneCurrencies() {
        if ((adenaInstance != null)
                && ((adenaInstance.getCount() <= 0) || (adenaInstance.getOwnerId() != getOwnerId()))) {
            adenaInstance = null;
        }

        if ((ancientAdenaInstance != null)
                && ((ancientAdenaInstance.getCount() <= 0) || (ancientAdenaInstance.getOwnerId() != getOwnerId()))) {
            ancientAdenaInstance = null;
        }
    }

    @Override
    public Item destroyItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        return destroyItem(process, item, item.getCount(), actor, reference);
    }

    @Override
    public Item destroyItem(ItemProcessType process, Item item, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item destroyedItem = super.destroyItem(process, item, count, actor, reference);

        if ((adenaInstance != null) && (adenaInstance.getCount() <= 0)) {
            adenaInstance = null;
        }

        if ((ancientAdenaInstance != null) && (ancientAdenaInstance.getCount() <= 0)) {
            ancientAdenaInstance = null;
        }

        if ((destroyedItem != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_ITEM_DESTROY, destroyedItem.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnPlayerItemDestroy(actor, destroyedItem), destroyedItem.getTemplate());
        }

        return destroyedItem;
    }

    @Override
    public Item destroyItem(ItemProcessType process, int objectId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = getItemByObjectId(objectId);
        return item == null ? null : destroyItem(process, item, count, actor, reference);
    }

    /**
     * Destroys unequipped items first; several non-stackable items are destroyed one by one.
     */
    @Override
    public Item destroyItemByItemId(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        try (Snapshot<Item> items = ScratchBuffers.empty()) {
            Item destroyItem = null;
            long unequipped = 0;
            for (Item item : freeItems(item -> item.getId() == itemId)) {
                items.add(item);
                if (!item.isEquipped()) {
                    unequipped++;
                }

                if ((destroyItem == null) || destroyItem.isEquipped()) {
                    destroyItem = item;
                }
            }

            if (destroyItem == null) {
                return null;
            }

            if (!destroyItem.isStackable() && (count > 1)) {
                if (unequipped < count) {
                    return null;
                }

                final InventoryUpdate iu = new InventoryUpdate();
                long destroyed = 0;
                for (Item item : items) {
                    if (!item.isEquipped() && (destroyItem(process, item, 1, actor, reference) != null)) {
                        iu.addRemovedItem(item);
                        if (++destroyed == count) {
                            owner.sendInventoryUpdate(iu);
                            refreshWeight();
                            return item;
                        }
                    }
                }
            }

            return destroyItem(process, destroyItem, count, actor, reference);
        }
    }

    @Override
    public Item dropItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item droppedItem = super.dropItem(process, item, actor, reference);
        forgetGoneCurrencies();
        notifyDropped(actor, droppedItem);
        return droppedItem;
    }

    @Override
    public Item dropItem(ItemProcessType process, int objectId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = super.dropItem(process, objectId, count, actor, reference);
        forgetGoneCurrencies();
        notifyDropped(actor, item);
        return item;
    }

    private static void notifyDropped(Player actor, Item item) {
        if ((item != null)
                && EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_DROP, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerItemDrop.builder()
                                    .player(actor)
                                    .item(item)
                                    .location(item.getLocation())
                                    .build(),
                            item.getTemplate());
        }
    }

    @Override
    protected void addItem(Item item) {
        // A re-added item replaces its own entry, so it must not count twice.
        if (item.isQuestItem() && !holds(item)) {
            questItemSize++;
        }

        super.addItem(item);
        if (item.getId() == ADENA_ID) {
            adenaInstance = item;
        } else if (item.getId() == ANCIENT_ADENA_ID) {
            ancientAdenaInstance = item;
        }
    }

    @Override
    protected boolean removeItem(Item item) {
        owner.removeItemFromShortcut(item.getObjectId());

        if (item.getObjectId() == owner.getActiveEnchantItemId()) {
            owner.setActiveEnchantItemId(Player.ID_NONE);
        }

        if (!super.removeItem(item)) {
            return false;
        }

        if (item.equals(adenaInstance)) {
            adenaInstance = null;
        } else if (item.equals(ancientAdenaInstance)) {
            ancientAdenaInstance = null;
        }

        if (item.isQuestItem()) {
            questItemSize--;
        }

        return true;
    }

    @Override
    protected void clearItems() {
        super.clearItems();
        adenaInstance = null;
        ancientAdenaInstance = null;
        questItemSize = 0;
    }

    public int getQuestSize() {
        WorldPartition.checkOwner();
        return sizeOf(true);
    }

    public int getNonQuestSize() {
        WorldPartition.checkOwner();
        return sizeOf(false);
    }

    private int sizeOf(boolean questInventory) {
        return questInventory ? questItemSize : liveItems().size() - questItemSize;
    }

    @Override
    public void refreshWeight() {
        WorldPartition.checkOwner();
        super.refreshWeight();
        owner.refreshOverloaded();
    }

    @Override
    public void restore() {
        WorldPartition.checkOwner();
        super.restore();
        findCurrencies();
    }

    @Override
    public void restore(List<StoredItem> stored) {
        WorldPartition.checkOwner();
        super.restore(stored);
        findCurrencies();
    }

    private void findCurrencies() {
        adenaInstance = getItemByItemIdIncludingPending(ADENA_ID);
        ancientAdenaInstance = getItemByItemIdIncludingPending(ANCIENT_ADENA_ID);
    }

    public boolean checkInventorySlotsAndWeight(
            List<ItemTemplate> itemList, boolean sendMessage, boolean sendSkillMessage) {
        WorldPartition.checkOwner();
        int lootWeight = 0;
        int requiredSlots = 0;
        if (itemList != null) {
            for (ItemTemplate item : itemList) {
                if (!item.isStackable() || (getInventoryItemCount(item.getId(), -1) <= 0)) {
                    requiredSlots++;
                }

                lootWeight += item.getWeight();
            }
        }

        final boolean inventoryStatusOK = validateCapacity(requiredSlots) && validateWeight(lootWeight);
        if (!inventoryStatusOK && sendMessage) {
            owner.sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
            if (sendSkillMessage) {
                owner.sendPacket(
                        SystemMessageId.WEIGHT_AND_VOLUME_LIMIT_HAVE_BEEN_EXCEEDED_THAT_SKILL_IS_CURRENTLY_UNAVAILABLE);
            }
        }

        return inventoryStatusOK;
    }

    public boolean validateCapacity(Item item) {
        WorldPartition.checkOwner();
        int slots = 0;
        if (!item.isStackable()
                || ((getInventoryItemCount(item.getId(), -1) <= 0)
                        && !item.getTemplate().hasExImmediateEffect())) {
            slots++;
        }

        return validateCapacity(slots, item.isQuestItem());
    }

    public boolean validateCapacityByItemId(int itemId) {
        WorldPartition.checkOwner();
        int slots = 0;
        final Item invItem = getItemByItemId(itemId);
        if ((invItem == null) || !invItem.isStackable()) {
            slots++;
        }

        return validateCapacity(
                slots, ItemData.getInstance().getTemplate(itemId).isQuestItem());
    }

    @Override
    public boolean validateCapacity(long slots) {
        WorldPartition.checkOwner();
        return validateCapacity(slots, false);
    }

    public boolean validateCapacity(long slots, boolean questItem) {
        WorldPartition.checkOwner();
        final boolean questInventory = ((slots == 0) && !PlayerConfig.AUTO_LOOT_SLOT_LIMIT) || questItem;
        final int limit = questInventory ? owner.getQuestInventoryLimit() : owner.getInventoryLimit();
        return (sizeOf(questInventory) + slots) <= limit;
    }

    @Override
    public boolean validateWeight(long weight) {
        WorldPartition.checkOwner();
        if (owner.isGM() && owner.getDietMode() && owner.getAccessLevel().allowTransaction()) {
            return true;
        }

        return ((totalWeight + weight) <= owner.getMaxLoad());
    }

    public void setInventoryBlock(int[] items, int mode) {
        WorldPartition.checkOwner();
        blockMode = mode;
        blockItems = items;

        owner.sendItemList(false);
    }

    public void unblock() {
        WorldPartition.checkOwner();
        blockMode = -1;
        blockItems = null;

        owner.sendItemList(false);
    }

    public boolean hasInventoryBlock() {
        WorldPartition.checkOwner();
        return (blockMode > -1) && (blockItems != null) && (blockItems.length > 0);
    }

    public void blockAllItems() {
        WorldPartition.checkOwner();
        // An empty list means no block, so allow-only mode gets one id past the item table.
        setInventoryBlock(new int[] {(ItemData.getInstance().getArraySize() + 2)}, 1);
    }

    public boolean canManipulateWithItemId(int itemId) {
        WorldPartition.checkOwner();
        return ((blockMode != 0) || !ArrayUtil.contains(blockItems, itemId))
                && ((blockMode != 1) || ArrayUtil.contains(blockItems, itemId));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + owner + "]";
    }

    /**
     * Shows the owner the items committed value transactions changed, each as its last change says.
     *
     * @param fullList whether the whole list follows, so the client drops a stack it may still show twice
     */
    public void sendCommittedChanges(List<Item> changed, boolean fullList) {
        WorldPartition.checkOwner();
        final InventoryUpdate update = new InventoryUpdate();
        update.addItems(changed);
        owner.sendInventoryUpdate(update);
        if (fullList) {
            owner.sendItemList(false);
        }

        final StatusUpdate load = new StatusUpdate(owner);
        load.addAttribute(StatusUpdate.CUR_LOAD, owner.getCurrentLoad());
        owner.sendPacket(load);
    }

    public void applyItemSkills() {
        WorldPartition.checkOwner();
        try (Snapshot<Item> items = ScratchBuffers.snapshot(liveItems())) {
            for (Item item : items) {
                item.giveSkillsToOwner();
                item.applyEnchantStats();
            }
        }
    }
}
