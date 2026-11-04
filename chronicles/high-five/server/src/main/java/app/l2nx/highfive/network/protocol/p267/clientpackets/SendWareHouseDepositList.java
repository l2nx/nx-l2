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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static app.l2nx.highfive.entity.itemcontainer.Inventory.ADENA_ID;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.itemcontainer.PlayerWarehouse;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EnchantResult;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * SendWareHouseDepositList client packet class.
 */
@Slf4j
public class SendWareHouseDepositList extends ClientPacket {
    private static final int BATCH_LENGTH = 12;

    private List<ItemHolder> items = null;

    @Override
    protected void readImpl() {
        final int size = readInt();
        if ((size <= 0) || (size > PlayerConfig.MAX_ITEM_IN_PACKET) || ((size * BATCH_LENGTH) != remaining())) {
            return;
        }

        items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final int objId = readInt();
            final long count = readLong();
            if ((objId < 1) || (count < 1)) {
                items = null;
                return;
            }

            items.add(new ItemHolder(objId, count));
        }
    }

    @Override
    protected void runImpl() {
        if (items == null) {
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You are depositing items too fast.");
            return;
        }

        final ItemContainer warehouse = player.getActiveWarehouse();
        if (warehouse == null) {
            return;
        }

        final Npc manager = player.getLastFolkNPC();
        if (((manager == null) || !manager.isWarehouse() || !manager.canInteract(player)) && !player.isGM()) {
            player.sendPacket(
                    SystemMessageId.YOU_FAILED_AT_SENDING_THE_PACKAGE_BECAUSE_YOU_ARE_TOO_FAR_FROM_THE_WAREHOUSE);
            return;
        }

        final boolean isPrivate = warehouse instanceof PlayerWarehouse;
        if (!isPrivate && !player.getAccessLevel().allowTransaction()) {
            player.sendMessage("Transactions are disabled for your Access Level.");
            return;
        }

        if (player.getActiveEnchantItemId() != Player.ID_NONE) {
            player.sendPacket(SystemMessageId.YOU_HAVE_CANCELLED_THE_ENCHANTING_PROCESS);
            player.sendPacket(new EnchantResult(2, 0, 0));
            player.setActiveEnchantItemId(Player.ID_NONE);
            return;
        }

        // Alt game - Karma punishment.
        if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && (player.getKarma() > 0)) {
            return;
        }

        if (player.getActiveTradeList() != null) {
            return;
        }

        final List<ItemHolder> deposits = new ArrayList<>(items.size());
        final IntSet handles = new IntOpenHashSet(items.size());
        long currentAdena = player.getAdena();
        int slots = 0;
        for (ItemHolder itemHolder : items) {
            final Item item = player.checkItemManipulation(itemHolder.getId(), itemHolder.getCount(), "deposit");
            if ((item == null) || !handles.add(itemHolder.getId())) {
                log.warn("Error depositing a warehouse object for char {} (validity check)", player.getName());
                return;
            }

            if (!item.isDepositable(isPrivate) || !item.isAvailable(player, true, isPrivate)) {
                continue;
            }

            deposits.add(itemHolder);
            if (item.getId() == ADENA_ID) {
                currentAdena -= itemHolder.getCount();
            }

            if (!item.isStackable()) {
                slots += itemHolder.getCount();
            } else if (warehouse.getItemByItemId(item.getId()) == null) {
                slots++;
            }
        }

        if (deposits.isEmpty()) {
            return;
        }

        if (!warehouse.validateCapacity(slots)) {
            player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_QUANTITY_THAT_CAN_BE_INPUTTED);
            return;
        }

        final long fee = deposits.size() * 30L;
        if ((currentAdena < fee) || !player.reduceAdena(ItemProcessType.FEE, fee, manager, false)) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
            return;
        }

        final InventoryUpdate playerIU = new InventoryUpdate();
        for (ItemHolder itemHolder : deposits) {
            final Item oldItem = player.getInventory().getItemByObjectId(itemHolder.getId());
            final Item newItem = player.getInventory()
                    .transferItem(
                            ItemProcessType.TRANSFER,
                            itemHolder.getId(),
                            itemHolder.getCount(),
                            warehouse,
                            player,
                            manager);
            if (newItem == null) {
                log.error("Paid deposit of item {} for char {} moved nothing", itemHolder.getId(), player.getName());
                continue;
            }

            if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
                playerIU.addModifiedItem(oldItem);
            } else {
                playerIU.addRemovedItem(oldItem);
            }
        }

        // Send updated item list to the player.
        player.sendInventoryUpdate(playerIU);

        // Update current load status on player.
        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
        player.sendPacket(su);
    }
}
