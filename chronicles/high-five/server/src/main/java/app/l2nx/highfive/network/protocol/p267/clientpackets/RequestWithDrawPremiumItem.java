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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.PremiumItem;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetPremiumItemList;

public class RequestWithDrawPremiumItem extends ClientPacket {
    private int itemNum;
    private int charId;
    private long itemCount;

    @Override
    protected void readImpl() {
        itemNum = readInt();
        charId = readInt();
        itemCount = readLong();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        } else if (itemCount < 1) {
            return;
        } else if (player.getObjectId() != charId) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "[RequestWithDrawPremiumItem] Incorrect owner, Player: " + player.getName(),
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        } else if (!player.hasPremiumItems()) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "[RequestWithDrawPremiumItem] Player: " + player.getName() + " try to get item with empty list!",
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        } else if ((player.getWeightPenalty() >= 3) || !player.isInventoryUnder90(false)) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_CANNOT_RECEIVE_THE_DIMENSIONAL_ITEM_BECAUSE_YOU_HAVE_EXCEED_YOUR_INVENTORY_WEIGHT_QUANTITY_LIMIT);
            return;
        } else if (player.isProcessingTransaction()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_RECEIVE_A_DIMENSIONAL_ITEM_DURING_AN_EXCHANGE);
            return;
        }

        final PremiumItem item = player.getPremiumItem(itemNum);
        if (item == null) {
            return;
        } else if (item.getCount() < itemCount) {
            return;
        }

        final long itemsLeft = (item.getCount() - itemCount);
        player.addItem(ItemProcessType.TRANSFER, item.getItemId(), itemCount, player.getTarget(), true);
        if (itemsLeft > 0) {
            item.updateCount(itemsLeft);
            player.updatePremiumItem(itemNum, itemsLeft);
        } else {
            player.removePremiumItem(itemNum);
            player.deletePremiumItem(itemNum);
        }

        if (!player.hasPremiumItems()) {
            player.sendPacket(SystemMessageId.THERE_ARE_NO_MORE_DIMENSIONAL_ITEMS_TO_BE_FOUND);
        } else {
            player.sendPacket(new ExGetPremiumItemList(player));
        }
    }
}
