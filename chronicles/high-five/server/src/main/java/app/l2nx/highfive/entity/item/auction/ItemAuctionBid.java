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
package app.l2nx.highfive.entity.item.auction;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author Forsaiken
 */
@SingleWriter
public class ItemAuctionBid {
    private final int playerObjId;

    private long lastBid;

    public ItemAuctionBid(int playerObjId, long lastBid) {
        this.playerObjId = playerObjId;
        this.lastBid = lastBid;
    }

    public int getPlayerObjId() {
        WorldPartition.checkOwner();
        return playerObjId;
    }

    public long getLastBid() {
        WorldPartition.checkOwner();
        return lastBid;
    }

    public void setLastBid(long lastBid) {
        WorldPartition.checkOwner();
        this.lastBid = lastBid;
    }

    public void cancelBid() {
        WorldPartition.checkOwner();
        lastBid = -1;
    }

    public boolean isCanceled() {
        WorldPartition.checkOwner();
        return lastBid <= 0;
    }

    public Player getPlayer() {
        WorldPartition.checkOwner();
        return World.getPlayer(playerObjId);
    }
}
