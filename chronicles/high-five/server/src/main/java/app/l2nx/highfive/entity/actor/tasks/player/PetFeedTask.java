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
package app.l2nx.highfive.entity.actor.tasks.player;

import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Task dedicated for feeding player's pet.
 * @author UnAfraid
 */
@Slf4j
public class PetFeedTask implements Runnable {

    private final Player player;

    public PetFeedTask(Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        try {
            if (!player.isMounted()
                    || (player.getMountNpcId() == 0)
                    || (PetDataTable.getInstance().getPetData(player.getMountNpcId()) == null)) {
                player.stopFeed();
                return;
            }

            if (player.getCurrentFeed() > player.getFeedConsume()) {
                // eat
                player.setCurrentFeed(player.getCurrentFeed() - player.getFeedConsume());
            } else {
                player.setCurrentFeed(0);
                player.stopFeed();

                if (player.isFlying() && (player.getMountType() == MountType.WYVERN)) {
                    player.dismount();
                    player.teleToLocation(TeleportWhereType.TOWN); // Teleport to nearest village.
                } else {
                    player.dismount();
                }

                player.sendPacket(SystemMessageId.YOU_ARE_OUT_OF_FEED_MOUNT_STATUS_CANCELED);
                return;
            }

            final Set<Integer> foodIds = PetDataTable.getInstance()
                    .getPetData(player.getMountNpcId())
                    .getFood();
            if (foodIds.isEmpty()) {
                return;
            }

            Item food = null;
            for (int id : foodIds) {
                // TODO: possibly pet inv?
                food = player.getInventory().getItemByItemId(id);
                if (food != null) {
                    break;
                }
            }

            if ((food != null) && player.isHungry()) {
                final IItemHandler handler = ItemHandler.getInstance().getHandler(food.getEtcItem());
                if (handler != null) {
                    handler.onItemUse(player, food, false);
                    final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PET_WAS_HUNGRY_SO_IT_ATE_S1);
                    sm.addItemName(food.getId());
                    player.sendPacket(sm);
                }
            }
        } catch (Exception e) {
            log.error("Mounted Pet [NpcId: {}] a feed task error has occurred", player.getMountNpcId(), e);
        }
    }
}
