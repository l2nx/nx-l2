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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.itemcontainer.PlayerFreight;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.StatusUpdate;
import lombok.extern.slf4j.Slf4j;

/**
 * @author -Wooden-, UnAfraid, mrTJO
 */
@Slf4j
public class RequestPackageSend extends ClientPacket {
    private static final int BATCH_LENGTH = 12; // length of the one item

    private ItemHolder[] items = null;
    private int objectId;

    @Override
    protected void readImpl() {
        objectId = readInt();

        final int count = readInt();
        if ((count <= 0) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining())) {
            return;
        }

        items = new ItemHolder[count];
        for (int i = 0; i < count; i++) {
            final int objId = readInt();
            final long cnt = readLong();
            if ((objId < 1) || (cnt < 0)) {
                items = null;
                return;
            }

            items[i] = new ItemHolder(objId, cnt);
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((items == null) || (player == null) || !player.getAccountChars().containsKey(objectId)) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You are sending freight packages too fast.");
            return;
        }

        final Npc manager = player.getLastFolkNPC();
        if (((manager == null) || !player.isInsideRadius2D(manager, Npc.INTERACTION_DISTANCE))) {
            return;
        }

        if (player.getActiveEnchantItemId() != Player.ID_NONE) {
            PunishmentManager.handleIllegalPlayerAction(
                    player, player + " tried to use enchant Exploit!", GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        // get current tradelist if any
        if (player.getActiveTradeList() != null) {
            return;
        }

        // Alt game - Karma punishment.
        if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && (player.getKarma() > 0)) {
            return;
        }

        // Freight price from config per item slot.
        final int fee = items.length * PlayerConfig.ALT_FREIGHT_PRICE;
        long currentAdena = player.getAdena();
        int slots = 0;

        final ItemContainer warehouse = new PlayerFreight(objectId);
        for (ItemHolder i : items) {
            // Check validity of requested item.
            final Item item = player.checkItemManipulation(i.getId(), i.getCount(), "freight");
            if (item == null) {
                log.warn("Error depositing a warehouse object for char {} (validity check)", player.getName());
                warehouse.deleteMe();
                return;
            } else if (!item.isFreightable()) {
                warehouse.deleteMe();
                return;
            }

            // Calculate needed adena and slots.
            if (item.getId() == Inventory.ADENA_ID) {
                currentAdena -= i.getCount();
            } else if (!item.isStackable()) {
                slots += i.getCount();
            } else if (warehouse.getItemByItemId(item.getId()) == null) {
                slots++;
            }
        }

        // Item Max Limit Check.
        if (!warehouse.validateCapacity(slots)) {
            player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_QUANTITY_THAT_CAN_BE_INPUTTED);
            warehouse.deleteMe();
            return;
        }

        // Check if enough adena and charge the fee.
        if ((currentAdena < fee) || !player.reduceAdena(ItemProcessType.FEE, fee, manager, false)) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
            warehouse.deleteMe();
            return;
        }

        // Proceed to the transfer.
        final InventoryUpdate playerIU = new InventoryUpdate();
        for (ItemHolder i : items) {
            // Check validity of requested item.
            final Item oldItem = player.checkItemManipulation(i.getId(), i.getCount(), "deposit");
            if (oldItem == null) {
                log.warn("Error depositing a warehouse object for char {} (olditem == null)", player.getName());
                warehouse.deleteMe();
                return;
            }

            final Item newItem = player.getInventory()
                    .transferItem(ItemProcessType.TRANSFER, i.getId(), i.getCount(), warehouse, player, null);
            if (newItem == null) {
                log.warn("Error depositing a warehouse object for char {} (newitem == null)", player.getName());
                continue;
            }

            if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
                playerIU.addModifiedItem(oldItem);
            } else {
                playerIU.addRemovedItem(oldItem);
            }

            // Remove item objects from the world. (redundant - warehouse.deleteMe() handles newItem; oldItem already
            // handled by transferItem)
            // World.removeObject(oldItem);
            // World.removeObject(newItem);
        }

        warehouse.deleteMe();

        // Send updated item list to the player.
        player.sendInventoryUpdate(playerIU);

        // Update current load status on player.
        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
        player.sendPacket(su);
    }
}
