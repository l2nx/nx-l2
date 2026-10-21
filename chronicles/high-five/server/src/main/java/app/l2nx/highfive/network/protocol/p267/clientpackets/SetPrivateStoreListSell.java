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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPrivateStoreSetWholeMsg;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreManageListSell;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreMsgSell;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;

/**
 * @version $Revision: 1.2.2.1.2.5 $ $Date: 2005/03/27 15:29:30 $
 */
public class SetPrivateStoreListSell extends ClientPacket {
    private static final int BATCH_LENGTH = 20; // length of the one item

    private boolean packageSale;
    private Item[] items = null;

    @Override
    protected void readImpl() {
        packageSale = (readInt() == 1);
        final int count = readInt();
        if ((count < 1) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining())) {
            return;
        }

        items = new Item[count];
        for (int i = 0; i < count; i++) {
            final int itemId = readInt();
            final long cnt = readLong();
            final long price = readLong();
            if ((itemId < 1) || (cnt < 1) || (price < 0)) {
                items = null;
                return;
            }

            items[i] = new Item(itemId, cnt, price);
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (items == null) {
            player.sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT);
            player.setPrivateStoreType(PrivateStoreType.NONE);
            player.broadcastUserInfo();
            return;
        }

        if (!player.getAccessLevel().allowTransaction()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        if (AttackStanceTaskManager.getInstance().hasAttackStanceTask(player) || player.isInDuel()) {
            player.sendPacket(
                    SystemMessageId
                            .WHILE_YOU_ARE_ENGAGED_IN_COMBAT_YOU_CANNOT_OPERATE_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
            player.sendPacket(new PrivateStoreManageListSell(player, packageSale));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isInsideZone(ZoneId.NO_STORE)) {
            player.sendPacket(new PrivateStoreManageListSell(player, packageSale));
            player.sendPacket(SystemMessageId.YOU_CANNOT_OPEN_A_PRIVATE_STORE_HERE);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (!player.canOpenPrivateStore()) {
            player.sendPacket(new PrivateStoreManageListSell(player, packageSale));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Check maximum number of allowed slots for pvt shops.
        if (items.length > player.getPrivateSellStoreLimit()) {
            player.sendPacket(new PrivateStoreManageListSell(player, packageSale));
            player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_QUANTITY_THAT_CAN_BE_INPUTTED);
            return;
        }

        final TradeList tradeList = player.getSellList();
        tradeList.clear();
        tradeList.setPackaged(packageSale);

        long totalCost = player.getAdena();
        for (Item i : items) {
            if (!i.addToTradeList(tradeList)) {
                player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
                return;
            }

            totalCost += i.getPrice();
            if (totalCost > PlayerConfig.MAX_ADENA) {
                player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
                return;
            }
        }

        player.sitDown();
        if (packageSale) {
            player.setPrivateStoreType(PrivateStoreType.PACKAGE_SELL);
        } else {
            player.setPrivateStoreType(PrivateStoreType.SELL);
        }

        player.broadcastUserInfo();

        if (packageSale) {
            player.broadcastPacket(new ExPrivateStoreSetWholeMsg(player));
        } else {
            player.broadcastPacket(new PrivateStoreMsgSell(player));
        }
    }

    private static class Item {
        private final int itemId;
        private final long count;
        private final long price;

        public Item(int id, long num, long pri) {
            itemId = id;
            count = num;
            price = pri;
        }

        public boolean addToTradeList(TradeList list) {
            if ((PlayerConfig.MAX_ADENA / count) < price) {
                return false;
            }

            list.addItem(itemId, count, price);
            return true;
        }

        public long getPrice() {
            return count * price;
        }
    }
}
