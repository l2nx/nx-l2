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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.EnchantItemData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enchant.EnchantScroll;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.PacketLogger;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ExPutEnchantTargetItemResult;

/**
 * @author KenM
 */
public class RequestExTryToPutEnchantTargetItem extends ClientPacket {
    private int _objectId;

    @Override
    protected void readImpl() {
        _objectId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((_objectId == 0) || (player == null)) {
            return;
        }

        if (player.isEnchanting()) {
            return;
        }

        final Item scroll = player.getInventory().getItemByObjectId(player.getActiveEnchantItemId());
        if (scroll == null) {
            return;
        }

        final Item item = player.getInventory().getItemByObjectId(_objectId);
        if (item == null) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "RequestExTryToPutEnchantTargetItem: " + player
                            + " tried to cheat using a packet manipulation tool! Ban this player!",
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        final EnchantScroll scrollTemplate = EnchantItemData.getInstance().getEnchantScroll(scroll);
        if (!item.isEnchantable() || (scrollTemplate == null) || !scrollTemplate.isValid(item, null)) {
            player.sendPacket(SystemMessageId.DOES_NOT_FIT_STRENGTHENING_CONDITIONS_OF_THE_SCROLL);
            player.setActiveEnchantItemId(Player.ID_NONE);
            player.sendPacket(new ExPutEnchantTargetItemResult(0));
            if (scrollTemplate == null) {
                PacketLogger.warning("RequestExTryToPutEnchantTargetItem: " + player
                        + " has used undefined scroll with id " + scroll.getId());
            }
            return;
        }

        player.setEnchanting(true);
        player.setActiveEnchantTimestamp(System.currentTimeMillis());
        player.sendPacket(new ExPutEnchantTargetItemResult(_objectId));
    }
}
