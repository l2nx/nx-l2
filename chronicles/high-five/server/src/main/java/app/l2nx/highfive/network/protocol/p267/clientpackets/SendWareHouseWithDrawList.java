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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.ClanWarehouse;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.itemcontainer.PlayerWarehouse;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EnchantResult;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import lombok.extern.slf4j.Slf4j;

/**
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2005/03/29 23:15:16 $
 */
@Slf4j
public class SendWareHouseWithDrawList extends ClientPacket {
    private static final int BATCH_LENGTH = 12; // length of the one item

    private ItemHolder[] items = null;

    @Override
    protected void readImpl() {
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
        if (items == null) {
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You are withdrawing items too fast.");
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

        if (!(warehouse instanceof PlayerWarehouse) && !player.getAccessLevel().allowTransaction()) {
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

        if (PlayerConfig.ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH) {
            if ((warehouse instanceof ClanWarehouse) && !player.hasAccess(ClanAccess.ACCESS_WAREHOUSE)) {
                return;
            }
        } else {
            if ((warehouse instanceof ClanWarehouse) && !player.isClanLeader()) {
                player.sendPacket(
                        SystemMessageId
                                .ITEMS_LEFT_AT_THE_CLAN_HALL_WAREHOUSE_CAN_ONLY_BE_RETRIEVED_BY_THE_CLAN_LEADER_DO_YOU_WANT_TO_CONTINUE);
                return;
            }
        }

        int weight = 0;
        int slots = 0;
        for (ItemHolder i : items) {
            // Calculate needed slots
            final Item item = warehouse.getItemByObjectId(i.getId());
            if ((item == null) || (item.getCount() < i.getCount())) {
                PunishmentManager.handleIllegalPlayerAction(
                        player,
                        "Warning!! Character " + player.getName() + " of account " + player.getAccountName()
                                + " tried to withdraw non-existent item from warehouse.",
                        GeneralConfig.DEFAULT_PUNISH);
                return;
            }

            weight += i.getCount() * item.getTemplate().getWeight();
            if (!item.isStackable()) {
                slots += i.getCount();
            } else if (player.getInventory().getItemByItemId(item.getId()) == null) {
                slots++;
            }
        }

        // Item Max Limit Check.
        if (!player.getInventory().validateCapacity(slots)) {
            player.sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
            return;
        }

        // Weight limit Check
        if (!player.getInventory().validateWeight(weight)) {
            player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
            return;
        }

        // Proceed to the transfer.
        final InventoryUpdate playerIU = new InventoryUpdate();
        for (ItemHolder i : items) {
            final Item oldItem = warehouse.getItemByObjectId(i.getId());
            if ((oldItem == null) || (oldItem.getCount() < i.getCount())) {
                log.warn("Error withdrawing a warehouse object for char {} (olditem == null)", player.getName());
                return;
            }

            final Item newItem = warehouse.transferItem(
                    ItemProcessType.TRANSFER, i.getId(), i.getCount(), player.getInventory(), player, manager);
            if (newItem == null) {
                log.warn("Error withdrawing a warehouse object for char {} (newitem == null)", player.getName());
                return;
            }

            if (newItem.getCount() > i.getCount()) {
                playerIU.addModifiedItem(newItem);
            } else {
                playerIU.addNewItem(newItem);
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
