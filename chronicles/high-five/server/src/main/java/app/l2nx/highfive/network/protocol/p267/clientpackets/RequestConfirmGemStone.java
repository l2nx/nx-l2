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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPutCommissionResultForVariationMake;

/**
 * Format:(ch) dddd
 * @author -Wooden-
 */
public class RequestConfirmGemStone extends AbstractRefinePacket {
    private int targetItemObjId;
    private int refinerItemObjId;
    private int gemstoneItemObjId;
    private long gemStoneCount;

    @Override
    protected void readImpl() {
        targetItemObjId = readInt();
        refinerItemObjId = readInt();
        gemstoneItemObjId = readInt();
        gemStoneCount = readLong();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final Item targetItem = player.getInventory().getItemByObjectId(targetItemObjId);
        if (targetItem == null) {
            return;
        }

        final Item refinerItem = player.getInventory().getItemByObjectId(refinerItemObjId);
        if (refinerItem == null) {
            return;
        }

        final Item gemStoneItem = player.getInventory().getItemByObjectId(gemstoneItemObjId);
        if (gemStoneItem == null) {
            return;
        }

        // Make sure the item is a gemstone
        if (!isValid(player, targetItem, refinerItem, gemStoneItem)) {
            player.sendPacket(SystemMessageId.THIS_IS_NOT_A_SUITABLE_ITEM);
            return;
        }

        // Check for gemstone count
        final LifeStone ls = getLifeStone(refinerItem.getId());
        if (ls == null) {
            return;
        }

        if (gemStoneCount != getGemStoneCount(targetItem.getTemplate().getCrystalType(), ls.getGrade())) {
            player.sendPacket(SystemMessageId.GEMSTONE_QUANTITY_IS_INCORRECT);
            return;
        }

        player.sendPacket(
                new ExPutCommissionResultForVariationMake(gemstoneItemObjId, gemStoneCount, gemStoneItem.getId()));
    }
}
