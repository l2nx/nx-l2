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

import static app.l2nx.highfive.entity.itemcontainer.Inventory.MAX_ADENA;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.RecipeData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.item.recipe.ManufactureItem;
import app.l2nx.highfive.entity.item.recipe.RecipeList;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.RecipeShopMsg;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;

/**
 * RequestRecipeShopListSet client packet class.
 */
public class RequestRecipeShopListSet extends ClientPacket {
    private static final int BATCH_LENGTH = 12;

    private ManufactureItem[] items = null;

    @Override
    protected void readImpl() {
        final int count = readInt();
        if ((count <= 0) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining())) {
            return;
        }

        items = new ManufactureItem[count];
        for (int i = 0; i < count; i++) {
            final int id = readInt();
            final long cost = readLong();
            if (cost < 0) {
                items = null;
                return;
            }

            items[i] = new ManufactureItem(id, cost);
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (items == null) {
            player.setPrivateStoreType(PrivateStoreType.NONE);
            player.broadcastUserInfo();
            return;
        }

        if (AttackStanceTaskManager.getInstance().hasAttackStanceTask(player) || player.isInDuel()) {
            player.sendPacket(
                    SystemMessageId
                            .WHILE_YOU_ARE_ENGAGED_IN_COMBAT_YOU_CANNOT_OPERATE_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isInsideZone(ZoneId.NO_STORE)) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_OPEN_A_PRIVATE_WORKSHOP_HERE);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (!player.canOpenPrivateStore()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.getManufactureItems().clear();
        for (ManufactureItem i : items) {
            final RecipeList list = RecipeData.getInstance().getRecipeList(i.getRecipeId());
            if (!player.getDwarvenRecipeBook().contains(list)
                    && !player.getCommonRecipeBook().contains(list)) {
                PunishmentManager.handleIllegalPlayerAction(
                        player,
                        "Warning!! " + player + " of account " + player.getAccountName()
                                + " tried to set recipe which he does not have.",
                        GeneralConfig.DEFAULT_PUNISH);
                return;
            }

            if (i.getCost() > MAX_ADENA) {
                PunishmentManager.handleIllegalPlayerAction(
                        player,
                        "Warning!! " + player + " of account " + player.getAccountName()
                                + " tried to set price more than " + MAX_ADENA + " adena in Private Manufacture.",
                        GeneralConfig.DEFAULT_PUNISH);
                return;
            }

            player.getManufactureItems().put(i.getRecipeId(), i);
        }

        player.setStoreName(!player.hasManufactureShop() ? "" : player.getStoreName());
        player.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
        player.sitDown();
        player.broadcastUserInfo();
        World.broadcastToSelfAndVisiblePlayers(player, new RecipeShopMsg(player));
    }
}
