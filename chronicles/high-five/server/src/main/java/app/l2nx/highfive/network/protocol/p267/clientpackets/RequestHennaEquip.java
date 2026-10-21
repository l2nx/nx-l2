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
import app.l2nx.highfive.data.xml.HennaData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Zoey76
 */
@Slf4j
public class RequestHennaEquip extends ClientPacket {
    private int symbolId;

    @Override
    protected void readImpl() {
        symbolId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            return;
        }

        if (player.getHennaEmptySlots() == 0) {
            player.sendPacket(SystemMessageId.NO_SLOT_EXISTS_TO_DRAW_THE_SYMBOL);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final Henna henna = HennaData.getInstance().getHenna(symbolId);
        if (henna == null) {
            log.warn("{}: Invalid Henna Id: {} from {}", getClass().getName(), symbolId, player);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final long count = player.getInventory().getInventoryItemCount(henna.getDyeItemId(), -1);
        if (henna.isAllowedClass(player.getPlayerClass())
                && (count >= henna.getWearCount())
                && (player.getAdena() >= henna.getWearFee())
                && player.addHenna(henna)) {
            player.destroyItemByItemId(ItemProcessType.FEE, henna.getDyeItemId(), henna.getWearCount(), player, true);
            player.getInventory().reduceAdena(ItemProcessType.FEE, henna.getWearFee(), player, player.getLastFolkNPC());
            final InventoryUpdate iu = new InventoryUpdate();
            iu.addModifiedItem(player.getInventory().getAdenaInstance());
            player.sendInventoryUpdate(iu);
            player.sendPacket(SystemMessageId.THE_SYMBOL_HAS_BEEN_ADDED);
        } else {
            player.sendPacket(SystemMessageId.THE_SYMBOL_CANNOT_BE_DRAWN);
            if (!player.isGM() && !henna.isAllowedClass(player.getPlayerClass())) {
                PunishmentManager.handleIllegalPlayerAction(
                        player,
                        "Exploit attempt: " + player + " tryed to add a forbidden henna.",
                        GeneralConfig.DEFAULT_PUNISH);
            }

            player.sendPacket(ActionFailed.STATIC_PACKET);
        }
    }
}
