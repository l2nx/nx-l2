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
package app.l2nx.highfive.network.holders;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Advi
 */
@Slf4j
public class TradeList {

    @Getter
    private final Player owner;

    @Getter
    @Setter
    private Player partner;

    private final Set<TradeItem> items = ConcurrentHashMap.newKeySet();

    @Getter
    @Setter
    private String title;

    @Getter
    @Setter
    private boolean packaged;

    @Getter
    private boolean confirmed = false;

    @Getter
    private boolean locked = false;

    public TradeList(Player owner) {
        this.owner = owner;
    }

    /**
     * @return all items from TradeList
     */
    public Collection<TradeItem> getItems() {
        return items;
    }

    /**
     * Returns the list of items in inventory available for transaction
     * @param inventory
     * @return Item : items in inventory
     */
    public Collection<TradeItem> getAvailableItems(PlayerInventory inventory) {
        final List<TradeItem> list = new ArrayList<>();
        for (TradeItem item : items) {
            item = new TradeItem(item, item.getCount(), item.getPrice());
            inventory.adjustAvailableItem(item);
            list.add(item);
        }

        return list;
    }

    /**
     * @return Item List size
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Adjust available item from Inventory by the one in this list
     * @param item : Item to be adjusted
     * @return TradeItem representing adjusted item
     */
    public TradeItem adjustAvailableItem(Item item) {
        if (item.isStackable()) {
            for (TradeItem exclItem : items) {
                if (exclItem.getItem().getId() == item.getId()) {
                    return item.getCount() <= exclItem.getCount()
                            ? null
                            : new TradeItem(item, item.getCount() - exclItem.getCount(), item.getReferencePrice());
                }
            }
        }

        return new TradeItem(item, item.getCount(), item.getReferencePrice());
    }

    /**
     * Add simplified item to TradeList
     * @param objectId : int
     * @param count : int
     * @return
     */
    public TradeItem addItem(int objectId, long count) {
        return addItem(objectId, count, 0);
    }

    /**
     * Add item to TradeList
     * @param objectId : int
     * @param count : long
     * @param price : long
     * @return
     */
    public synchronized TradeItem addItem(int objectId, long count, long price) {
        if (locked) {
            return null;
        }

        final WorldObject o = World.findObject(objectId);
        if (!(o instanceof Item)) {
            return null;
        }

        final Item item = (Item) o;
        if (!(item.isTradeable() || (owner.isGM() && GeneralConfig.GM_TRADE_RESTRICTED_ITEMS)) || item.isQuestItem()) {
            return null;
        }

        if (!owner.getInventory().canManipulateWithItemId(item.getId())) {
            return null;
        }

        if ((count <= 0) || (count > item.getCount())) {
            return null;
        }

        if (!item.isStackable() && (count > 1)) {
            return null;
        }

        if ((PlayerConfig.MAX_ADENA / count) < price) {
            return null;
        }

        for (TradeItem checkitem : items) {
            if (checkitem.getObjectId() == objectId) {
                return null;
            }
        }

        final TradeItem titem = new TradeItem(item, count, price);
        items.add(titem);

        // If Player has already confirmed this trade, invalidate the confirmation.
        invalidateConfirmation();
        return titem;
    }

    /**
     * Add item to TradeList
     * @param itemId
     * @param count
     * @param price
     * @return
     */
    public synchronized TradeItem addItemByItemId(int itemId, long count, long price) {
        if (locked) {
            log.warn("{}: Attempt to modify locked TradeList!", owner.getName());
            return null;
        }

        final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
        if (item == null) {
            log.warn("{}: Attempt to add invalid item to TradeList!", owner.getName());
            return null;
        }

        if (!item.isTradeable() || item.isQuestItem()) {
            return null;
        }

        if (!item.isStackable() && (count > 1)) {
            log.warn("{}: Attempt to add non-stackable item to TradeList with count > 1!", owner.getName());
            return null;
        }

        if ((PlayerConfig.MAX_ADENA / count) < price) {
            log.warn("{}: Attempt to overflow adena !", owner.getName());
            return null;
        }

        final TradeItem titem = new TradeItem(item, count, price);
        items.add(titem);

        // If Player has already confirmed this trade, invalidate the confirmation.
        invalidateConfirmation();
        return titem;
    }

    /**
     * Remove item from TradeList
     * @param objectId : int
     * @param itemId
     * @param count : int
     * @return
     */
    private synchronized TradeItem removeItem(int objectId, int itemId, long count) {
        if (locked) {
            log.warn("{}: Attempt to modify locked TradeList!", owner.getName());
            return null;
        }

        for (TradeItem titem : items) {
            if ((titem.getObjectId() == objectId) || (titem.getItem().getId() == itemId)) {
                // If Partner has already confirmed this trade, invalidate the confirmation.
                if (partner != null) {
                    final TradeList partnerList = partner.getActiveTradeList();
                    if (partnerList == null) {
                        log.warn(
                                "{}: Trading partner ({}) is invalid in this trade!",
                                partner.getName(),
                                partner.getName());
                        return null;
                    }

                    partnerList.invalidateConfirmation();
                }

                // Reduce item count or complete item.
                if ((count != -1) && (titem.getCount() > count)) {
                    titem.setCount(titem.getCount() - count);
                } else {
                    items.remove(titem);
                }

                return titem;
            }
        }

        return null;
    }

    /**
     * Update items in TradeList according their quantity in owner inventory
     */
    public synchronized void updateItems() {
        for (TradeItem titem : items) {
            final Item item = owner.getInventory().getItemByObjectId(titem.getObjectId());
            if ((item == null) || (titem.getCount() < 1)) {
                removeItem(titem.getObjectId(), -1, -1);
            } else if (item.getCount() < titem.getCount()) {
                titem.setCount(item.getCount());
            }
        }
    }

    /**
     * Lockes TradeList, no further changes are allowed
     */
    public void lock() {
        locked = true;
    }

    /**
     * Clears item list
     */
    public synchronized void clear() {
        items.clear();
        locked = false;
    }

    /**
     * Confirms TradeList
     * @return : boolean
     */
    public boolean confirm() {
        if (confirmed) {
            return true; // Already confirmed
        }

        // If Partner has already confirmed this trade, proceed exchange.
        if (partner != null) {
            final TradeList partnerList = partner.getActiveTradeList();
            if (partnerList == null) {
                log.warn("{}: Trading partner ({}) is invalid in this trade!", partner.getName(), partner.getName());
                return false;
            }

            // Synchronization order to avoid deadlock.
            TradeList sync1;
            TradeList sync2;
            if (getOwner().getObjectId() > partnerList.getOwner().getObjectId()) {
                sync1 = partnerList;
                sync2 = this;
            } else {
                sync1 = this;
                sync2 = partnerList;
            }

            synchronized (sync1) {
                synchronized (sync2) {
                    confirmed = true;
                    if (partnerList.isConfirmed()) {
                        partnerList.lock();
                        lock();
                        if (!partnerList.validate() || !validate()) {
                            return false;
                        }

                        doExchange(partnerList);
                    } else {
                        partner.onTradeConfirm(owner);
                    }
                }
            }
        } else {
            confirmed = true;
        }

        return confirmed;
    }

    /**
     * Cancels TradeList confirmation
     */
    private void invalidateConfirmation() {
        confirmed = false;
    }

    /**
     * Validates TradeList with owner inventory
     * @return
     */
    private boolean validate() {
        // Check for Owner validity.
        if ((owner == null) || (World.getPlayer(owner.getObjectId()) == null)) {
            log.warn("Invalid owner of TradeList");
            return false;
        }

        // Check for Item validity.
        for (TradeItem titem : items) {
            final Item item = owner.checkItemManipulation(titem.getObjectId(), titem.getCount(), "transfer");
            if ((item == null) || (item.getCount() < 1)) {
                log.warn("{}: Invalid Item in TradeList", owner.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Transfers all TradeItems from inventory to partner
     * @param partner
     * @param ownerIU
     * @param partnerIU
     * @return
     */
    private boolean TransferItems(Player partner, InventoryUpdate ownerIU, InventoryUpdate partnerIU) {
        for (TradeItem titem : items) {
            final Item oldItem = owner.getInventory().getItemByObjectId(titem.getObjectId());
            if (oldItem == null) {
                return false;
            }

            final Item newItem = owner.getInventory()
                    .transferItem(
                            ItemProcessType.TRANSFER,
                            titem.getObjectId(),
                            titem.getCount(),
                            partner.getInventory(),
                            owner,
                            this.partner);
            if (newItem == null) {
                return false;
            }

            // Add changes to inventory update packets.
            if (ownerIU != null) {
                if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
                    ownerIU.addModifiedItem(oldItem);
                } else {
                    ownerIU.addRemovedItem(oldItem);
                }
            }

            if (partnerIU != null) {
                if (newItem.getCount() > titem.getCount()) {
                    partnerIU.addModifiedItem(newItem);
                } else {
                    partnerIU.addNewItem(newItem);
                }
            }
        }

        return true;
    }

    /**
     * @param partner
     * @return items slots count
     */
    private int countItemsSlots(Player partner) {
        int slots = 0;
        for (TradeItem item : items) {
            if (item == null) {
                continue;
            }

            final ItemTemplate template =
                    ItemData.getInstance().getTemplate(item.getItem().getId());
            if (template == null) {
                continue;
            }

            if (!template.isStackable()) {
                slots += item.getCount();
            } else if (partner.getInventory().getItemByItemId(item.getItem().getId()) == null) {
                slots++;
            }
        }

        return slots;
    }

    /**
     * @return the weight of items in tradeList
     */
    private int calcItemsWeight() {
        long weight = 0;
        for (TradeItem item : items) {
            if (item == null) {
                continue;
            }

            final ItemTemplate template =
                    ItemData.getInstance().getTemplate(item.getItem().getId());
            if (template == null) {
                continue;
            }

            weight += item.getCount() * template.getWeight();
        }

        return (int) Math.min(weight, Integer.MAX_VALUE);
    }

    /**
     * Proceeds with trade
     * @param partnerList
     */
    private void doExchange(TradeList partnerList) {
        boolean success = false;

        // Check weight and slots.
        if (!getOwner().getInventory().validateWeight(partnerList.calcItemsWeight())
                || !partnerList.getOwner().getInventory().validateWeight(calcItemsWeight())) {
            partnerList.getOwner().sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
            owner.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
        } else if (!getOwner().getInventory().validateCapacity(partnerList.countItemsSlots(getOwner()))
                || !partnerList.getOwner().getInventory().validateCapacity(countItemsSlots(partnerList.getOwner()))) {
            partnerList.getOwner().sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
            owner.sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
        } else {
            // Prepare inventory update packet.
            final InventoryUpdate ownerIU = new InventoryUpdate();
            final InventoryUpdate partnerIU = new InventoryUpdate();

            // Transfer items
            partnerList.TransferItems(owner, partnerIU, ownerIU);
            TransferItems(partnerList.getOwner(), ownerIU, partnerIU);
            owner.sendInventoryUpdate(ownerIU);
            partner.sendInventoryUpdate(partnerIU);

            // The client drops part of the update when the trade window closes, so the full list is sent as well.
            owner.sendItemList(false);
            partner.sendItemList(false);

            // Update current load as well.
            StatusUpdate playerSU = new StatusUpdate(owner);
            playerSU.addAttribute(StatusUpdate.CUR_LOAD, owner.getCurrentLoad());
            owner.sendPacket(playerSU);
            playerSU = new StatusUpdate(partner);
            playerSU.addAttribute(StatusUpdate.CUR_LOAD, partner.getCurrentLoad());
            partner.sendPacket(playerSU);

            success = true;
        }

        // Finish the trade
        partnerList.getOwner().onTradeFinish(success);
        owner.onTradeFinish(success);
    }

    /**
     * Buy items from this PrivateStore list
     * @param player
     * @param items
     * @return int: result of trading. 0 - ok, 1 - canceled (no adena), 2 - failed (item error)
     */
    public synchronized int privateStoreBuy(Player player, Set<RequestTrade> items) {
        if (locked) {
            return 1;
        }

        if (!validate()) {
            lock();
            return 1;
        }

        if (!owner.isOnline() || !player.isOnline()) {
            return 1;
        }

        int slots = 0;
        int weight = 0;
        long totalPrice = 0;

        final PlayerInventory ownerInventory = owner.getInventory();
        final PlayerInventory playerInventory = player.getInventory();
        for (RequestTrade item : items) {
            boolean found = false;
            for (TradeItem ti : this.items) {
                if (ti.getObjectId() == item.getObjectId()) {
                    if (ti.getPrice() == item.getPrice()) {
                        if (ti.getCount() < item.getCount()) {
                            item.setCount(ti.getCount());
                        }

                        found = true;
                    }
                    break;
                }
            }

            // item with this objectId and price not found in tradelist
            if (!found) {
                if (packaged) {
                    PunishmentManager.handleIllegalPlayerAction(
                            player,
                            "[TradeList.privateStoreBuy()] " + player
                                    + " tried to cheat the package sell and buy only a part of the package! Ban this"
                                    + " player for bot usage!",
                            GeneralConfig.DEFAULT_PUNISH);
                    return 2;
                }

                item.setCount(0);
                continue;
            }

            // Check for overflow in the single item.
            if ((PlayerConfig.MAX_ADENA / item.getCount()) < item.getPrice()) {
                // private store attempting to overflow - disable it
                lock();
                return 1;
            }

            totalPrice += item.getCount() * item.getPrice();

            // Check for overflow of the total price.
            if ((PlayerConfig.MAX_ADENA < totalPrice) || (totalPrice < 0)) {
                // private store attempting to overflow - disable it
                lock();
                return 1;
            }

            // Check if requested item is available for manipulation.
            final Item oldItem = owner.checkItemManipulation(item.getObjectId(), item.getCount(), "sell");
            if ((oldItem == null) || !oldItem.isTradeable()) {
                // private store sell invalid item - disable it
                lock();
                return 2;
            }

            final ItemTemplate template = ItemData.getInstance().getTemplate(item.getItemId());
            if (template == null) {
                continue;
            }

            weight += item.getCount() * template.getWeight();
            if (!template.isStackable()) {
                slots += item.getCount();
            } else if (playerInventory.getItemByItemId(item.getItemId()) == null) {
                slots++;
            }
        }

        if (totalPrice > playerInventory.getAdena()) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
            return 1;
        }

        if (!playerInventory.validateWeight(weight)) {
            player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
            return 1;
        }

        if (!playerInventory.validateCapacity(slots)) {
            player.sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
            return 1;
        }

        final InventoryUpdate ownerIU = new InventoryUpdate();
        final InventoryUpdate playerIU = new InventoryUpdate();
        if (!takeAdena(playerInventory, totalPrice, player, owner, playerIU)) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
            return 1;
        }

        ownerInventory.addAdena(ItemProcessType.SELL, totalPrice, owner, player);

        // ownerIU.addItem(ownerInventory.getAdenaInstance());
        boolean ok = true;

        // Transfer items
        for (RequestTrade item : items) {
            if (item.getCount() == 0) {
                continue;
            }

            // Check if requested item is available for manipulation.
            final Item oldItem = owner.checkItemManipulation(item.getObjectId(), item.getCount(), "sell");
            if (oldItem == null) {
                // Should not happens - validation already done.
                lock();
                ok = false;
                break;
            }

            // Proceed with item transfer.
            final Item newItem = ownerInventory.transferItem(
                    ItemProcessType.TRANSFER, item.getObjectId(), item.getCount(), playerInventory, owner, player);
            if (newItem == null) {
                ok = false;
                break;
            }

            removeItem(item.getObjectId(), -1, item.getCount());

            // Add changes to inventory update packets.
            if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
                ownerIU.addModifiedItem(oldItem);
            } else {
                ownerIU.addRemovedItem(oldItem);
            }

            if (newItem.getCount() > item.getCount()) {
                playerIU.addModifiedItem(newItem);
            } else {
                playerIU.addNewItem(newItem);
            }

            // Send messages about the transaction to both players.
            SystemMessage msg;
            if (newItem.isStackable()) {
                msg = new SystemMessage(SystemMessageId.C1_PURCHASED_S3_S2_S);
                msg.addString(player.getName());
                msg.addItemName(newItem);
                msg.addLong(item.getCount());
                owner.sendPacket(msg);
                msg = new SystemMessage(SystemMessageId.YOU_HAVE_PURCHASED_S3_S2_S_FROM_C1);
                msg.addString(owner.getName());
                msg.addItemName(newItem);
                msg.addLong(item.getCount());
            } else {
                msg = new SystemMessage(SystemMessageId.C1_PURCHASED_S2);
                msg.addString(player.getName());
                msg.addItemName(newItem);
                owner.sendPacket(msg);
                msg = new SystemMessage(SystemMessageId.YOU_HAVE_PURCHASED_S2_FROM_C1);
                msg.addString(owner.getName());
                msg.addItemName(newItem);
            }

            player.sendPacket(msg);
        }

        // Send inventory update packet.
        owner.sendInventoryUpdate(ownerIU);
        player.sendInventoryUpdate(playerIU);
        return ok ? 0 : 2;
    }

    /**
     * Sell items to this PrivateStore list
     * @param player
     * @param items
     * @return : boolean true if success
     */
    public synchronized boolean privateStoreSell(Player player, RequestTrade[] items) {
        if (locked || !owner.isOnline() || !player.isOnline()) {
            return false;
        }

        boolean ok = false;

        final PlayerInventory ownerInventory = owner.getInventory();
        final PlayerInventory playerInventory = player.getInventory();

        // Prepare inventory update packet.
        final InventoryUpdate ownerIU = new InventoryUpdate();
        final InventoryUpdate playerIU = new InventoryUpdate();
        long totalPrice = 0;
        for (RequestTrade item : items) {
            // Searching item in tradelist using itemId.
            boolean found = false;
            for (TradeItem ti : this.items) {
                if (ti.getItem().getId() == item.getItemId()) {
                    // Price should be the same.
                    if (ti.getPrice() == item.getPrice()) {
                        // If requesting more than available - decrease count.
                        if (ti.getCount() < item.getCount()) {
                            item.setCount(ti.getCount());
                        }

                        found = item.getCount() > 0;
                    }
                    break;
                }
            }

            // Not found any item in the tradelist with same itemId and price.
            // Maybe another player already sold this item?
            if (!found) {
                continue;
            }

            // Check for overflow in the single item.
            if ((PlayerConfig.MAX_ADENA / item.getCount()) < item.getPrice()) {
                lock();
                break;
            }

            final long newTotalPrice = totalPrice + (item.getCount() * item.getPrice());

            // Check for overflow of the total price.
            if ((PlayerConfig.MAX_ADENA < newTotalPrice) || (newTotalPrice < 0)) {
                lock();
                break;
            }

            if (ownerInventory.getAdena() < newTotalPrice) {
                continue;
            }

            // Check if requested item is available for manipulation.
            int objectId = item.getObjectId();
            Item oldItem = player.checkItemManipulation(objectId, item.getCount(), "sell");
            // private store - buy use same objectId for buying several non-stackable items
            if (oldItem == null) {
                // searching other items using same itemId
                oldItem = playerInventory.getItemByItemId(item.getItemId());
                if (oldItem == null) {
                    continue;
                }

                objectId = oldItem.getObjectId();
                oldItem = player.checkItemManipulation(objectId, item.getCount(), "sell");
                if (oldItem == null) {
                    continue;
                }
            }

            if (oldItem.getId() != item.getItemId()) {
                PunishmentManager.handleIllegalPlayerAction(
                        player, player + " is cheating with sell items", GeneralConfig.DEFAULT_PUNISH);
                return false;
            }

            if (!oldItem.isTradeable()) {
                continue;
            }

            // Proceed with item transfer.
            final Item newItem = playerInventory.transferItem(
                    ItemProcessType.TRANSFER, objectId, item.getCount(), ownerInventory, player, owner);
            if (newItem == null) {
                continue;
            }

            removeItem(-1, item.getItemId(), item.getCount());
            ok = true;

            // Increase total price only after successful transaction.
            totalPrice = newTotalPrice;

            // Add changes to inventory update packets.
            if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
                playerIU.addModifiedItem(oldItem);
            } else {
                playerIU.addRemovedItem(oldItem);
            }

            if (newItem.getCount() > item.getCount()) {
                ownerIU.addModifiedItem(newItem);
            } else {
                ownerIU.addNewItem(newItem);
            }

            // Send messages about the transaction to both players.
            SystemMessage msg;
            if (newItem.isStackable()) {
                msg = new SystemMessage(SystemMessageId.YOU_HAVE_PURCHASED_S3_S2_S_FROM_C1);
                msg.addString(player.getName());
                msg.addItemName(newItem);
                msg.addLong(item.getCount());
                owner.sendPacket(msg);
                msg = new SystemMessage(SystemMessageId.C1_PURCHASED_S3_S2_S);
                msg.addString(owner.getName());
                msg.addItemName(newItem);
                msg.addLong(item.getCount());
            } else {
                msg = new SystemMessage(SystemMessageId.YOU_HAVE_PURCHASED_S2_FROM_C1);
                msg.addString(player.getName());
                msg.addItemName(newItem);
                owner.sendPacket(msg);
                msg = new SystemMessage(SystemMessageId.C1_PURCHASED_S2);
                msg.addString(owner.getName());
                msg.addItemName(newItem);
            }

            player.sendPacket(msg);
        }

        if (totalPrice > 0) {
            // Transfer adena
            if (!takeAdena(ownerInventory, totalPrice, owner, player, ownerIU)) {
                // should not happens, just a precaution
                return false;
            }

            playerInventory.addAdena(ItemProcessType.SELL, totalPrice, player, owner);
            playerIU.addItem(playerInventory.getAdenaInstanceIncludingPending());
        }

        if (ok) {
            // Send inventory update packet.
            owner.sendInventoryUpdate(ownerIU);
            player.sendInventoryUpdate(playerIU);
        }

        return ok;
    }

    // Spending all adena destroys the stack, which the client must then see removed, not left at its old count.
    private static boolean takeAdena(
            PlayerInventory inventory, long count, Player actor, Player reference, InventoryUpdate update) {
        final Item adena =
                inventory.destroyItemByItemId(ItemProcessType.BUY, Inventory.ADENA_ID, count, actor, reference);
        if (adena == null) {
            return false;
        }

        if (adena.getCount() > 0) {
            update.addModifiedItem(adena);
        } else {
            update.addRemovedItem(adena);
        }

        return true;
    }
}
