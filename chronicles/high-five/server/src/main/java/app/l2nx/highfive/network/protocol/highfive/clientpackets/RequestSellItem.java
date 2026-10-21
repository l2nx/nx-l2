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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import static app.l2nx.highfive.entity.actor.Npc.INTERACTION_DISTANCE;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.MerchantZeroSellPriceConfig;
import app.l2nx.highfive.data.xml.BuyListData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Merchant;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.UniqueItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.buylist.BuyListHolder;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExBuySellList;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.StatusUpdate;
import java.util.ArrayList;
import java.util.List;

/**
 * RequestSellItem client packet class.
 */
public class RequestSellItem extends ClientPacket {
    private static final int BATCH_LENGTH = 16;
    private static final int CUSTOM_CB_SELL_LIST = 423;

    private int listId;
    private List<UniqueItemHolder> items = null;

    @Override
    protected void readImpl() {
        listId = readInt();
        final int size = readInt();
        if ((size <= 0) || (size > PlayerConfig.MAX_ITEM_IN_PACKET) || ((size * BATCH_LENGTH) != remaining())) {
            return;
        }

        items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final int objectId = readInt();
            final int itemId = readInt();
            final long count = readLong();
            if ((objectId < 1) || (itemId < 1) || (count < 1)) {
                items = null;
                return;
            }

            items.add(new UniqueItemHolder(itemId, objectId, count));
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You are selling too fast.");
            return;
        }

        if (items == null) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Alt game - Karma punishment.
        if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_SHOP && (player.getKarma() > 0)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final WorldObject target = player.getTarget();
        Creature merchant = null;
        if (!player.isGM() && (listId != CUSTOM_CB_SELL_LIST)) {
            if ((target == null)
                    || (!player.isInsideRadius3D(target, INTERACTION_DISTANCE))
                    || (player.getInstanceId() != target.getInstanceId())) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            if (target instanceof Merchant) {
                merchant = target.asCreature();
            } else {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }
        }

        if ((merchant == null) && !player.isGM() && (listId != CUSTOM_CB_SELL_LIST)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final BuyListHolder buyList = BuyListData.getInstance().getBuyList(listId);
        if (buyList == null) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "Warning!! Character " + player.getName() + " of account " + player.getAccountName()
                            + " sent a false BuyList list_id " + listId,
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        if ((merchant != null) && !buyList.isNpcAllowed(merchant.getId())) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        long totalPrice = 0;

        // Proceed the sell
        for (UniqueItemHolder i : items) {
            final Item item = player.checkItemManipulation(i.getObjectId(), i.getCount(), "sell");
            if ((item == null) || (!item.isSellable())) {
                continue;
            }

            final long price = item.getReferencePrice() / 2;
            totalPrice += price * i.getCount();
            if (((PlayerConfig.MAX_ADENA / i.getCount()) < price) || (totalPrice > PlayerConfig.MAX_ADENA)) {
                player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
                return;
            }

            if (GeneralConfig.ALLOW_REFUND) {
                player.getInventory()
                        .transferItem(
                                ItemProcessType.TRANSFER,
                                i.getObjectId(),
                                i.getCount(),
                                player.getRefund(),
                                player,
                                merchant);
            } else {
                player.getInventory()
                        .destroyItem(ItemProcessType.SELL, i.getObjectId(), i.getCount(), player, merchant);
            }
        }

        if (!MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE) {
            player.addAdena(ItemProcessType.SELL, totalPrice, merchant, false);
        }

        // Update current load as well.
        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
        player.sendPacket(su);
        player.sendPacket(new ExBuySellList(player, true));
        player.sendPacket(SystemMessageId.THE_TRANSACTION_IS_COMPLETE);
    }
}
