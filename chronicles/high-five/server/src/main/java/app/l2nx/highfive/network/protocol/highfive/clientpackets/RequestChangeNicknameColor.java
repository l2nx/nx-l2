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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;

/**
 * @author KenM, Gnacik
 */
public class RequestChangeNicknameColor extends ClientPacket {
    private static final int[] COLORS = {
        0x9393FF, // Pink
        0x7C49FC, // Rose Pink
        0x97F8FC, // Lemon Yellow
        0xFA9AEE, // Lilac
        0xFF5D93, // Cobalt Violet
        0x00FCA0, // Mint Green
        0xA0A601, // Peacock Green
        0x7898AF, // Yellow Ochre
        0x486295, // Chocolate
        0x999999, // Silver
    };

    private int colorNum;
    private int itemObjectId;
    private String title;

    @Override
    protected void readImpl() {
        colorNum = readInt();
        title = readString();
        itemObjectId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if ((colorNum < 0) || (colorNum >= COLORS.length)) {
            return;
        }

        final Item item = player.getInventory().getItemByObjectId(itemObjectId);
        if ((item == null)
                || (item.getEtcItem() == null)
                || (item.getEtcItem().getHandlerName() == null)
                || !item.getEtcItem().getHandlerName().equalsIgnoreCase("NicknameColor")) {
            return;
        }

        if (player.destroyItem(ItemProcessType.NONE, item, 1, null, true)) {
            player.setTitle(title);
            player.getAppearance().setTitleColor(COLORS[colorNum]);
            player.broadcastUserInfo();
        }
    }
}
