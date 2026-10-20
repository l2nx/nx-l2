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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.auction.ItemAuction;
import app.l2nx.highfive.entity.item.auction.ItemAuctionInstance;
import app.l2nx.highfive.managers.ItemAuctionManager;

/**
 * @author Forsaiken
 */
public class RequestBidItemAuction extends ClientPacket {
    private int instanceId;
    private long bid;

    @Override
    protected void readImpl() {
        instanceId = readInt();
        bid = readLong();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Can't perform transaction here.
        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You are bidding too fast.");
            return;
        }

        if ((bid < 0) || (bid > PlayerConfig.MAX_ADENA)) {
            return;
        }

        final ItemAuctionInstance instance = ItemAuctionManager.getInstance().getManagerInstance(instanceId);
        if (instance != null) {
            final ItemAuction auction = instance.getCurrentAuction();
            if (auction != null) {
                auction.registerBid(player, bid);
            }
        }
    }
}
