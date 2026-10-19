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
package app.l2nx.highfive.network.clientpackets;

import static app.l2nx.highfive.entity.actor.Npc.INTERACTION_DISTANCE;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.holders.RequestTrade;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestPrivateStoreBuy extends ClientPacket {
    private static final int BATCH_LENGTH = 20; // length of the one item

    private int storePlayerId;
    private Set<RequestTrade> items = null;

    @Override
    protected void readImpl() {
        storePlayerId = readInt();
        final int count = readInt();
        if ((count <= 0) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining())) {
            return;
        }

        items = new HashSet<>();
        for (int i = 0; i < count; i++) {
            final int objectId = readInt();
            final long cnt = readLong();
            final long price = readLong();
            if ((objectId < 1) || (cnt < 1) || (price < 0)) {
                items = null;
                return;
            }

            items.add(new RequestTrade(objectId, cnt, price));
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (items == null) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You are buying items too fast.");
            return;
        }

        final WorldObject object = World.getPlayer(storePlayerId);
        if (object == null) {
            return;
        }

        if (player.isCursedWeaponEquipped()) {
            return;
        }

        final Player storePlayer = object.asPlayer();
        if (!player.isInsideRadius3D(storePlayer, INTERACTION_DISTANCE)) {
            return;
        }

        if ((player.getInstanceId() != storePlayer.getInstanceId()) && (player.getInstanceId() != -1)) {
            return;
        }

        if (!((storePlayer.getPrivateStoreType() == PrivateStoreType.SELL)
                || (storePlayer.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL))) {
            return;
        }

        final TradeList storeList = storePlayer.getSellList();
        if (storeList == null) {
            return;
        }

        if (!player.getAccessLevel().allowTransaction()) {
            player.sendMessage("Transactions are disabled for your Access Level.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if ((storePlayer.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL)
                && (storeList.getItemCount() > items.size())) {
            final String msgErr = "[RequestPrivateStoreBuy] " + player
                    + " tried to buy less items than sold by package-sell, ban this player for bot usage!";
            PunishmentManager.handleIllegalPlayerAction(player, msgErr, GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        final int result = storeList.privateStoreBuy(player, items);
        if (result > 0) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            if (result > 1) {
                log.warn(
                        "PrivateStore buy has failed due to invalid list or request. Player: {}, Private store of: {}",
                        player.getName(),
                        storePlayer.getName());
            }
            return;
        }

        // Update offline trade record, if realtime saving is enabled.
        if (OfflineTradeConfig.OFFLINE_TRADE_ENABLE
                && OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME
                && ((storePlayer.getClient() == null) || storePlayer.getClient().isDetached())) {
            OfflineTraderTable.getInstance().onTransaction(storePlayer, storeList.getItemCount() == 0, false);
        }

        if (storeList.getItemCount() == 0) {
            storePlayer.setPrivateStoreType(PrivateStoreType.NONE);
            storePlayer.broadcastUserInfo();
        }
    }
}
