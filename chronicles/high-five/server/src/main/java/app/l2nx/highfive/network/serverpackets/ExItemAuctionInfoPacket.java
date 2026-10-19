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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.item.auction.ItemAuction;
import app.l2nx.highfive.entity.item.auction.ItemAuctionBid;
import app.l2nx.highfive.entity.item.auction.ItemAuctionState;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author Forsaiken
 */
public class ExItemAuctionInfoPacket extends AbstractItemPacket {
    private final boolean refresh;
    private final int timeRemaining;
    private final ItemAuction currentAuction;
    private final ItemAuction nextAuction;

    public ExItemAuctionInfoPacket(boolean refresh, ItemAuction currentAuction, ItemAuction nextAuction) {
        if (currentAuction == null) {
            throw new NullPointerException();
        }

        if (currentAuction.getAuctionState() != ItemAuctionState.STARTED) {
            timeRemaining = 0;
        } else {
            timeRemaining = (int) (currentAuction.getFinishingTimeRemaining() / 1000); // in seconds
        }

        this.refresh = refresh;
        this.currentAuction = currentAuction;
        this.nextAuction = nextAuction;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_ITEM_AUCTION_INFO.writeId(this, buffer);
        buffer.writeByte(!refresh);
        buffer.writeInt(currentAuction.getInstanceId());
        final ItemAuctionBid highestBid = currentAuction.getHighestBid();
        buffer.writeLong(highestBid != null ? highestBid.getLastBid() : currentAuction.getAuctionInitBid());
        buffer.writeInt(timeRemaining);
        writeItem(currentAuction.getItemInfo(), buffer);
        if (nextAuction != null) {
            buffer.writeLong(nextAuction.getAuctionInitBid());
            buffer.writeInt((int) (nextAuction.getStartingTime() / 1000)); // unix time in seconds
            writeItem(nextAuction.getItemInfo(), buffer);
        }
    }
}
