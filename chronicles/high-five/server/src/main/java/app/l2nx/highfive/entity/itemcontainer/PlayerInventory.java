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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.ItemData;
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
import app.l2nx.highfive.mechanics.variables.ItemVariables;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.TradeItem;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.StatusUpdate;
import app.l2nx.highfive.util.ArrayUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlayerInventory extends Inventory {

    @Getter
    private final Player owner;

    @Getter
    private Item adenaInstance;

    @Getter
    private Item ancientAdenaInstance;

    private final AtomicInteger questItemSize = new AtomicInteger();

    @Getter
    private int[] blockItems = null;

    /**
     * Block modes:
     * <ul>
     * <li>-1 - no block
     * <li>0 - block items from _invItems, allow usage of other items
     * <li>1 - allow usage of items from _invItems, block other items
     * </ul>
     */
    @Getter
    private int blockMode = -1;

    public PlayerInventory(Player owner) {
        this.owner = owner;
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
        return adenaInstance != null ? adenaInstance.getCount() : 0;
    }

    public long getAncientAdena() {
        return (ancientAdenaInstance != null) ? ancientAdenaInstance.getCount() : 0;
    }

    /**
     * Retrieves a collection of unique buyable items based on the specified conditions.
     * @param allowAdena {@code true} to include Adena in the results, {@code false} to exclude it.
     * @param allowAncientAdena {@code true} to include Ancient Adena in the results, {@code false} to exclude it.
     * @param onlyAvailable {@code true} to include only items that are currently available to the owner, {@code false} to include all items regardless of availability.
     * @param checkSellable {@code true} to check if items can be sold.
     * @return a collection of {@link Item} objects that match the specified criteria.
     */
    public Collection<Item> getUniqueItems(
            boolean allowAdena, boolean allowAncientAdena, boolean onlyAvailable, boolean checkSellable) {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if (!allowAdena && (item.getId() == ADENA_ID)) {
                continue;
            }

            if (!allowAncientAdena && (item.getId() == ANCIENT_ADENA_ID)) {
                continue;
            }

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

        return result;
    }

    /**
     * Retrieves a collection of unique items based on their enchant level and the specified conditions.
     * @param allowAdena {@code true} to include Adena in the results, {@code false} to exclude it.
     * @param allowAncientAdena {@code true} to include Ancient Adena in the results, {@code false} to exclude it.
     * @param onlyAvailable {@code true} to include only items that are currently available to the owner, {@code false} to include all items regardless of availability.
     * @return a collection of {@link Item} objects that match the specified criteria and have unique IDs and enchant levels.
     */
    public Collection<Item> getUniqueItemsByEnchantLevel(
            boolean allowAdena, boolean allowAncientAdena, boolean onlyAvailable) {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if ((item == null)
                    || (!allowAdena && (item.getId() == ADENA_ID))
                    || (!allowAncientAdena && (item.getId() == ANCIENT_ADENA_ID))) {
                continue;
            }

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

        return result;
    }

    /**
     * Returns the list of all items in inventory that have a given item id.
     * @param itemId : ID of item
     * @param includeEquipped : include equipped items
     * @return Collection<Item> : matching items from inventory
     */
    public Collection<Item> getAllItemsByItemId(int itemId, boolean includeEquipped) {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if ((itemId == item.getId()) && (includeEquipped || !item.isEquipped())) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * @param itemId
     * @param enchantment
     * @return
     */
    public Collection<Item> getAllItemsByItemId(int itemId, int enchantment) {
        return getAllItemsByItemId(itemId, enchantment, true);
    }

    /**
     * Returns the list of all items in inventory that have a given item id AND a given enchantment level.
     * @param itemId : ID of item
     * @param enchantment : enchant level of item
     * @param includeEquipped : include equipped items
     * @return Collection<Item> : matching items from inventory
     */
    public Collection<Item> getAllItemsByItemId(int itemId, int enchantment, boolean includeEquipped) {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if ((itemId == item.getId())
                    && (item.getEnchantLevel() == enchantment)
                    && (includeEquipped || !item.isEquipped())) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * @param allowAdena
     * @param allowNonTradeable
     * @param feightable
     * @return the list of items in inventory available for transaction
     */
    public Collection<Item> getAvailableItems(boolean allowAdena, boolean allowNonTradeable, boolean feightable) {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if (!item.isAvailable(owner, allowAdena, allowNonTradeable) || !canManipulateWithItemId(item.getId())) {
                continue;
            } else if (feightable) {
                if ((item.getItemLocation() == ItemLocation.INVENTORY) && item.isFreightable()) {
                    result.add(item);
                }
                continue;
            }

            result.add(item);
        }

        return result;
    }

    /**
     * Get all augmented items
     * @return
     */
    public Collection<Item> getAugmentedItems() {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if (item.isAugmented()) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Get all element items
     * @return
     */
    public Collection<Item> getElementItems() {
        final Collection<Item> result = new ArrayDeque<>();
        for (Item item : items) {
            if (item.getElementals() != null) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Returns the list of items in inventory available for transaction adjusted by tradeList
     * @param tradeList
     * @return Item : items in inventory
     */
    public Collection<TradeItem> getAvailableItems(TradeList tradeList) {
        final Collection<TradeItem> result = new ArrayDeque<>();
        for (Item item : items) {
            if ((item != null) && item.isAvailable(owner, false, false)) {
                final TradeItem adjItem = tradeList.adjustAvailableItem(item);
                if (adjItem != null) {
                    result.add(adjItem);
                }
            }
        }

        return result;
    }

    /**
     * Adjust TradeItem according his status in inventory
     * @param item : Item to be adjusted
     */
    public void adjustAvailableItem(TradeItem item) {
        boolean notAllEquipped = false;
        for (Item adjItem : getAllItemsByItemId(item.getItem().getId())) {
            if (adjItem.isEquipable()) {
                if (!adjItem.isEquipped()) {
                    notAllEquipped |= true;
                }
            } else {
                notAllEquipped |= true;
                break;
            }
        }

        if (notAllEquipped) {
            final Item adjItem = getItemByItemId(item.getItem().getId());
            item.setObjectId(adjItem.getObjectId());
            item.setEnchant(adjItem.getEnchantLevel());

            if (adjItem.getCount() < item.getCount()) {
                item.setCount(adjItem.getCount());
            }

            return;
        }

        item.setCount(0);
    }

    /**
     * Adds adena to PcInventory
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int Quantity of adena to be added
     * @param actor : Player Player requesting the item add
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     */
    public void addAdena(ItemProcessType process, long count, Player actor, Object reference) {
        if (count > 0) {
            addItem(process, ADENA_ID, count, actor, reference);
        }
    }

    /**
     * Removes adena to PcInventory
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int Quantity of adena to be removed
     * @param actor : Player Player requesting the item add
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return boolean : true if adena was reduced
     */
    public boolean reduceAdena(ItemProcessType process, long count, Player actor, Object reference) {
        if (count > 0) {
            return destroyItemByItemId(process, ADENA_ID, count, actor, reference) != null;
        }

        return false;
    }

    /**
     * Adds specified amount of ancient adena to player inventory.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int Quantity of adena to be added
     * @param actor : Player Player requesting the item add
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     */
    public void addAncientAdena(ItemProcessType process, long count, Player actor, Object reference) {
        if (count > 0) {
            addItem(process, ANCIENT_ADENA_ID, count, actor, reference);
        }
    }

    /**
     * Removes specified amount of ancient adena from player inventory.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int Quantity of adena to be removed
     * @param actor : Player Player requesting the item add
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return boolean : true if adena was reduced
     */
    public boolean reduceAncientAdena(ItemProcessType process, long count, Player actor, Object reference) {
        return (count > 0) && (destroyItemByItemId(process, ANCIENT_ADENA_ID, count, actor, reference) != null);
    }

    /**
     * Adds item in inventory and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be added
     * @param actor : Player Player requesting the item add
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the new item or the updated item in inventory
     */
    @Override
    public Item addItem(ItemProcessType process, Item item, Player actor, Object reference) {
        final Item addedItem = super.addItem(process, item, actor, reference);
        if (addedItem != null) {
            if ((addedItem.getId() == ADENA_ID) && !addedItem.equals(adenaInstance)) {
                adenaInstance = addedItem;
            } else if ((addedItem.getId() == ANCIENT_ADENA_ID) && !addedItem.equals(ancientAdenaInstance)) {
                ancientAdenaInstance = addedItem;
            }

            // Notify to scripts
            if ((actor != null) && EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_ADD, actor)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnPlayerItemAdd(actor, addedItem), actor);
            }
        }

        return addedItem;
    }

    /**
     * Adds item in inventory and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param itemId : int Item Identifier of the item to be added
     * @param count : int Quantity of items to be added
     * @param actor : Player Player requesting the item creation
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the new item or the updated item in inventory
     */
    @Override
    public Item addItem(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        final Item item = super.addItem(process, itemId, count, actor, reference);
        if (item != null) {
            if ((item.getId() == ADENA_ID) && !item.equals(adenaInstance)) {
                adenaInstance = item;
            } else if ((item.getId() == ANCIENT_ADENA_ID) && !item.equals(ancientAdenaInstance)) {
                ancientAdenaInstance = item;
            }

            if (actor != null) {
                // Send inventory update packet.
                final InventoryUpdate playerIU = new InventoryUpdate();
                if (item.isStackable() && (item.getCount() > count)) {
                    playerIU.addModifiedItem(item);
                } else {
                    playerIU.addNewItem(item);
                }

                actor.sendInventoryUpdate(playerIU);

                // Update current load as well.
                final StatusUpdate su = new StatusUpdate(actor);
                su.addAttribute(StatusUpdate.CUR_LOAD, actor.getCurrentLoad());
                actor.sendPacket(su);

                // Notify to scripts
                if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_ADD, actor)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnPlayerItemAdd(actor, item), actor);
                }
            }
        }

        return item;
    }

    /**
     * Transfers item to another inventory and checks _adena and _ancientAdena
     * @param process ItemProcessType identifier of process triggering this action
     * @param objectId Item Identifier of the item to be transfered
     * @param count Quantity of items to be transfered
     * @param target the item container for the item to be transfered.
     * @param actor the player requesting the item transfer
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the new item or the updated item in inventory
     */
    @Override
    public Item transferItem(
            ItemProcessType process, int objectId, long count, ItemContainer target, Player actor, Object reference) {
        final Item item = super.transferItem(process, objectId, count, target, actor, reference);

        if ((adenaInstance != null)
                && ((adenaInstance.getCount() <= 0) || (adenaInstance.getOwnerId() != getOwnerId()))) {
            adenaInstance = null;
        }

        if ((ancientAdenaInstance != null)
                && ((ancientAdenaInstance.getCount() <= 0) || (ancientAdenaInstance.getOwnerId() != getOwnerId()))) {
            ancientAdenaInstance = null;
        }

        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_TRANSFER, item.getTemplate())) {
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

    /**
     * Destroy item from inventory and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be destroyed
     * @param actor : Player requesting the item destroy
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the destroyed item or the updated item in inventory
     */
    @Override
    public Item destroyItem(ItemProcessType process, Item item, Player actor, Object reference) {
        return destroyItem(process, item, item.getCount(), actor, reference);
    }

    /**
     * Destroy item from inventory and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be destroyed
     * @param actor : Player requesting the item destroy
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the destroyed item or the updated item in inventory
     */
    @Override
    public Item destroyItem(ItemProcessType process, Item item, long count, Player actor, Object reference) {
        final Item destroyedItem = super.destroyItem(process, item, count, actor, reference);

        if ((adenaInstance != null) && (adenaInstance.getCount() <= 0)) {
            adenaInstance = null;
        }

        if ((ancientAdenaInstance != null) && (ancientAdenaInstance.getCount() <= 0)) {
            ancientAdenaInstance = null;
        }

        // Notify to scripts
        if ((destroyedItem != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_ITEM_DESTROY, destroyedItem.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnPlayerItemDestroy(actor, destroyedItem), destroyedItem.getTemplate());
        }

        return destroyedItem;
    }

    /**
     * Destroys item from inventory and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param objectId : int Item Instance identifier of the item to be destroyed
     * @param count : int Quantity of items to be destroyed
     * @param actor : Player requesting the item destroy
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the destroyed item or the updated item in inventory
     */
    @Override
    public Item destroyItem(ItemProcessType process, int objectId, long count, Player actor, Object reference) {
        final Item item = getItemByObjectId(objectId);
        return item == null ? null : destroyItem(process, item, count, actor, reference);
    }

    /**
     * Destroy item from inventory by using its <b>itemId</b> and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param itemId : int Item identifier of the item to be destroyed
     * @param count : int Quantity of items to be destroyed
     * @param actor : Player requesting the item destroy
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the destroyed item or the updated item in inventory
     */
    @Override
    public Item destroyItemByItemId(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        // Attempt to find non equipped items.
        Item destroyItem = null;
        final Collection<Item> items = getAllItemsByItemId(itemId);
        for (Item item : items) {
            destroyItem = item;
            if (!destroyItem.isEquipped()) {
                break;
            }
        }

        // No item found.
        if (destroyItem == null) {
            return null;
        }

        // Support destroying multiple non stackable items.
        if (!destroyItem.isStackable() && (count > 1)) {
            if (getInventoryItemCount(itemId, -1, false) >= count) {
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
            } else {
                return null;
            }
        }

        // Single item or stackable.
        return destroyItem(process, destroyItem, count, actor, reference);
    }

    /**
     * Drop item from inventory and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be dropped
     * @param actor : Player Player requesting the item drop
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the destroyed item or the updated item in inventory
     */
    @Override
    public Item dropItem(ItemProcessType process, Item item, Player actor, Object reference) {
        final Item droppedItem = super.dropItem(process, item, actor, reference);

        if ((adenaInstance != null)
                && ((adenaInstance.getCount() <= 0) || (adenaInstance.getOwnerId() != getOwnerId()))) {
            adenaInstance = null;
        }

        if ((ancientAdenaInstance != null)
                && ((ancientAdenaInstance.getCount() <= 0) || (ancientAdenaInstance.getOwnerId() != getOwnerId()))) {
            ancientAdenaInstance = null;
        }

        // Notify to scripts
        if ((droppedItem != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_ITEM_DROP, droppedItem.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerItemDrop.builder()
                                    .player(actor)
                                    .item(droppedItem)
                                    .location(droppedItem.getLocation())
                                    .build(),
                            droppedItem.getTemplate());
        }

        return droppedItem;
    }

    /**
     * Drop item from inventory by using its <b>objectID</b> and checks _adena and _ancientAdena
     * @param process : ItemProcessType identifier of process triggering this action
     * @param objectId : int Item Instance identifier of the item to be dropped
     * @param count : int Quantity of items to be dropped
     * @param actor : Player Player requesting the item drop
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the destroyed item or the updated item in inventory
     */
    @Override
    public Item dropItem(ItemProcessType process, int objectId, long count, Player actor, Object reference) {
        final Item item = super.dropItem(process, objectId, count, actor, reference);

        if ((adenaInstance != null)
                && ((adenaInstance.getCount() <= 0) || (adenaInstance.getOwnerId() != getOwnerId()))) {
            adenaInstance = null;
        }

        if ((ancientAdenaInstance != null)
                && ((ancientAdenaInstance.getCount() <= 0) || (ancientAdenaInstance.getOwnerId() != getOwnerId()))) {
            ancientAdenaInstance = null;
        }

        // Notify to scripts
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

        return item;
    }

    /**
     * Adds item to inventory for further adjustments.
     * @param item : Item to be added from inventory
     */
    @Override
    protected void addItem(Item item) {
        if (item.isQuestItem()) {
            questItemSize.incrementAndGet();
        }

        super.addItem(item);
    }

    /**
     * <b>Overloaded</b>, when removes item from inventory, remove also owner shortcuts.
     * @param item : Item to be removed from inventory
     */
    @Override
    protected boolean removeItem(Item item) {
        // Removes any reference to the item from Shortcut bar.
        owner.removeItemFromShortcut(item.getObjectId());

        // Removes active Enchant Scroll.
        if (item.getObjectId() == owner.getActiveEnchantItemId()) {
            owner.setActiveEnchantItemId(Player.ID_NONE);
        }

        if (item.getId() == ADENA_ID) {
            adenaInstance = null;
        } else if (item.getId() == ANCIENT_ADENA_ID) {
            ancientAdenaInstance = null;
        }

        if (item.isQuestItem()) {
            questItemSize.decrementAndGet();
        }

        return super.removeItem(item);
    }

    /**
     * @return the quantity of quest items in the inventory
     */
    public int getQuestSize() {
        return questItemSize.get();
    }

    /**
     * @return the quantity of items in the inventory
     */
    public int getNonQuestSize() {
        return items.size() - questItemSize.get();
    }

    /**
     * Refresh the weight of equipment loaded
     */
    @Override
    public void refreshWeight() {
        super.refreshWeight();
        owner.refreshOverloaded();
    }

    /**
     * Get back items in inventory from database
     */
    @Override
    public void restore() {
        super.restore();
        adenaInstance = getItemByItemId(ADENA_ID);
        ancientAdenaInstance = getItemByItemId(ANCIENT_ADENA_ID);
    }

    public static int[][] restoreVisibleInventory(int objectId) {
        final int[][] paperdoll = new int[31][3];
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT object_id,item_id,loc_data,enchant_level FROM items WHERE owner_id=? AND loc='PAPERDOLL'")) {
            ps.setInt(1, objectId);
            try (ResultSet invdata = ps.executeQuery()) {
                while (invdata.next()) {
                    final int slot = invdata.getInt("loc_data");
                    paperdoll[slot][0] = invdata.getInt("object_id");
                    if (TransmogConfig.ENABLE_TRANSMOG) {
                        final ItemVariables vars = new ItemVariables(paperdoll[slot][0]);
                        paperdoll[slot][1] = vars.getInt(ItemVariables.TRANSMOG_ID, invdata.getInt("item_id"));
                    } else {
                        final int itemId = invdata.getInt("item_id");
                        final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
                        paperdoll[slot][1] = template == null ? itemId : template.getDisplayId();
                    }

                    paperdoll[slot][2] = invdata.getInt("enchant_level");
                }
            }
        } catch (Exception e) {
            log.warn("Could not restore inventory: {}", e.getMessage(), e);
        }

        return paperdoll;
    }

    /**
     * @param itemList the items that needs to be validated.
     * @param sendMessage if {@code true} will send a message of inventory full.
     * @param sendSkillMessage if {@code true} will send a message of skill not available.
     * @return {@code true} if the inventory isn't full after taking new items and items weight add to current load doesn't exceed max weight load.
     */
    public boolean checkInventorySlotsAndWeight(
            List<ItemTemplate> itemList, boolean sendMessage, boolean sendSkillMessage) {
        int lootWeight = 0;
        int requiredSlots = 0;
        if (itemList != null) {
            for (ItemTemplate item : itemList) {
                // If the item is not stackable or is stackable and not present in inventory, will need a slot.
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

    /**
     * If the item is not stackable or is stackable and not present in inventory, will need a slot.
     * @param item the item to validate.
     * @return {@code true} if there is enough room to add the item inventory.
     */
    public boolean validateCapacity(Item item) {
        int slots = 0;
        if (!item.isStackable()
                || ((getInventoryItemCount(item.getId(), -1) <= 0)
                        && !item.getTemplate().hasExImmediateEffect())) {
            slots++;
        }

        return validateCapacity(slots, item.isQuestItem());
    }

    /**
     * If the item is not stackable or is stackable and not present in inventory, will need a slot.
     * @param itemId the item Id for the item to validate.
     * @return {@code true} if there is enough room to add the item inventory.
     */
    public boolean validateCapacityByItemId(int itemId) {
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
        return validateCapacity(slots, false);
    }

    public boolean validateCapacity(long slots, boolean questItem) {
        return ((slots == 0) && !PlayerConfig.AUTO_LOOT_SLOT_LIMIT) || questItem
                ? (getQuestSize() + slots) <= owner.getQuestInventoryLimit()
                : (getNonQuestSize() + slots) <= owner.getInventoryLimit();
    }

    @Override
    public boolean validateWeight(long weight) {
        // Disable weight check for GMs.
        if (owner.isGM() && owner.getDietMode() && owner.getAccessLevel().allowTransaction()) {
            return true;
        }

        return ((totalWeight + weight) <= owner.getMaxLoad());
    }

    /**
     * Set inventory block for specified IDs<br>
     * array reference is used for {@link PlayerInventory#blockItems}
     * @param items array of Ids to block/allow
     * @param mode blocking mode {@link PlayerInventory#blockMode}
     */
    public void setInventoryBlock(int[] items, int mode) {
        blockMode = mode;
        blockItems = items;

        owner.sendItemList(false);
    }

    /**
     * Unblock blocked itemIds
     */
    public void unblock() {
        blockMode = -1;
        blockItems = null;

        owner.sendItemList(false);
    }

    /**
     * Check if player inventory is in block mode.
     * @return true if some itemIds blocked
     */
    public boolean hasInventoryBlock() {
        return (blockMode > -1) && (blockItems != null) && (blockItems.length > 0);
    }

    /**
     * Block all player items
     */
    public void blockAllItems() {
        // temp fix, some id must be sended
        setInventoryBlock(new int[] {(ItemData.getInstance().getArraySize() + 2)}, 1);
    }

    /**
     * Check if player can use item by itemid
     * @param itemId int
     * @return true if can use
     */
    public boolean canManipulateWithItemId(int itemId) {
        return ((blockMode != 0) || !ArrayUtil.contains(blockItems, itemId))
                && ((blockMode != 1) || ArrayUtil.contains(blockItems, itemId));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + owner + "]";
    }

    /**
     * Apply skills of inventory items
     */
    public void applyItemSkills() {
        for (Item item : items) {
            item.giveSkillsToOwner();
            item.applyEnchantStats();
        }
    }
}
