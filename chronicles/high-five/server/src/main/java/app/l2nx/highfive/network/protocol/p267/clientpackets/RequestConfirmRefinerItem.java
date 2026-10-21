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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.CrystalType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPutIntensiveResultForVariationMake;

/**
 * Fromat(ch) dd
 * @author -Wooden-
 */
public class RequestConfirmRefinerItem extends AbstractRefinePacket {
    private int targetItemObjId;
    private int refinerItemObjId;

    @Override
    protected void readImpl() {
        targetItemObjId = readInt();
        refinerItemObjId = readInt();
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

        if (!isValid(player, targetItem, refinerItem)) {
            player.sendPacket(SystemMessageId.THIS_IS_NOT_A_SUITABLE_ITEM);
            return;
        }

        final int refinerItemId = refinerItem.getTemplate().getId();
        final CrystalType grade = targetItem.getTemplate().getCrystalType();
        final LifeStone ls = getLifeStone(refinerItemId);
        final int gemStoneId = getGemStoneId(grade);
        final int gemStoneCount = getGemStoneCount(grade, ls.getGrade());
        player.sendPacket(
                new ExPutIntensiveResultForVariationMake(refinerItemObjId, refinerItemId, gemStoneId, gemStoneCount));
    }
}
