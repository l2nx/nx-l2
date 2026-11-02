/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.auction.model.ItemAuctionBidRow;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Forsaiken
 */
@SingleWriter
public class ItemAuction {

    private static final long ENDING_TIME_EXTEND_5 = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
    private static final long ENDING_TIME_EXTEND_3 = TimeUnit.MILLISECONDS.convert(3, TimeUnit.MINUTES);

    private final int auctionId;

    private final int instanceId;

    private final long startingTime;

    private long endingTime;

    private final AuctionItem auctionItem;
    private final List<ItemAuctionBid> auctionBids;

    private ItemAuctionState auctionState;

    private ItemAuctionExtendState scheduledAuctionEndingExtendState;

    private ItemAuctionExtendState auctionEndingExtendState;

    private final ItemInfo itemInfo;

    private ItemAuctionBid highestBid;

    private int lastBidPlayerObjId;

    public ItemAuction(int auctionId, int instanceId, long startingTime, long endingTime, AuctionItem auctionItem) {
        this(auctionId, instanceId, startingTime, endingTime, auctionItem, new ArrayList<>(), ItemAuctionState.CREATED);
    }

    public ItemAuction(
            int auctionId,
            int instanceId,
            long startingTime,
            long endingTime,
            AuctionItem auctionItem,
            List<ItemAuctionBid> auctionBids,
            ItemAuctionState auctionState) {
        this.auctionId = auctionId;
        this.instanceId = instanceId;
        this.startingTime = startingTime;
        this.endingTime = endingTime;
        this.auctionItem = auctionItem;
        this.auctionBids = auctionBids;
        this.auctionState = auctionState;
        scheduledAuctionEndingExtendState = ItemAuctionExtendState.INITIAL;
        auctionEndingExtendState = ItemAuctionExtendState.INITIAL;

        final Item item = this.auctionItem.createNewItemInstance();
        itemInfo = new ItemInfo(item);
        World.removeObject(item);

        for (ItemAuctionBid bid : this.auctionBids) {
            if ((highestBid == null) || (highestBid.getLastBid() < bid.getLastBid())) {
                highestBid = bid;
            }
        }
    }

    public int getAuctionId() {
        WorldPartition.checkOwner();
        return auctionId;
    }

    public int getInstanceId() {
        WorldPartition.checkOwner();
        return instanceId;
    }

    public long getStartingTime() {
        WorldPartition.checkOwner();
        return startingTime;
    }

    public long getEndingTime() {
        WorldPartition.checkOwner();
        return endingTime;
    }

    public ItemAuctionExtendState getScheduledAuctionEndingExtendState() {
        WorldPartition.checkOwner();
        return scheduledAuctionEndingExtendState;
    }

    public void setScheduledAuctionEndingExtendState(ItemAuctionExtendState scheduledAuctionEndingExtendState) {
        WorldPartition.checkOwner();
        this.scheduledAuctionEndingExtendState = scheduledAuctionEndingExtendState;
    }

    public ItemAuctionExtendState getAuctionEndingExtendState() {
        WorldPartition.checkOwner();
        return auctionEndingExtendState;
    }

    public ItemInfo getItemInfo() {
        WorldPartition.checkOwner();
        return itemInfo;
    }

    public ItemAuctionBid getHighestBid() {
        WorldPartition.checkOwner();
        return highestBid;
    }

    public ItemAuctionState getAuctionState() {
        WorldPartition.checkOwner();
        return auctionState;
    }

    public boolean setAuctionState(ItemAuctionState expected, ItemAuctionState wanted) {
        WorldPartition.checkOwner();
        if (auctionState != expected) {
            return false;
        }

        auctionState = wanted;
        ItemAuctionManager.getInstance().updateAuctionState(auctionId, auctionState);
        return true;
    }

    public Item createNewItemInstance() {
        WorldPartition.checkOwner();
        return auctionItem.createNewItemInstance();
    }

    public long getAuctionInitBid() {
        WorldPartition.checkOwner();
        return auctionItem.getAuctionInitBid();
    }

    public long getFinishingTimeRemaining() {
        WorldPartition.checkOwner();
        return Math.max(endingTime - ClockBridge.wallMillis(), 0);
    }

    private int getAndSetLastBidPlayerObjectId(int playerObjId) {
        final int lastBid = lastBidPlayerObjId;
        lastBidPlayerObjId = playerObjId;
        return lastBid;
    }

    private void updatePlayerBid(ItemAuctionBid bid, boolean delete) {
        if (delete) {
            ItemAuctionManager.getInstance().deleteBid(auctionId, bid.getPlayerObjId());
        } else {
            ItemAuctionManager.getInstance()
                    .saveBid(
                            auctionId,
                            ItemAuctionBidRow.builder()
                                    .characterId(bid.getPlayerObjId())
                                    .lastBid(bid.getLastBid())
                                    .build());
        }
    }

    public void registerBid(Player player, long newBid) {
        WorldPartition.checkOwner();
        if (player == null) {
            throw new NullPointerException();
        }

        if (newBid < auctionItem.getAuctionInitBid()) {
            player.sendPacket(SystemMessageId.YOUR_BID_PRICE_MUST_BE_HIGHER_THAN_THE_MINIMUM_PRICE_CURRENTLY_BEING_BID);
            return;
        }

        if (newBid > 100000000000L) {
            player.sendPacket(
                    SystemMessageId.BIDDING_IS_NOT_ALLOWED_BECAUSE_THE_MAXIMUM_BIDDING_PRICE_EXCEEDS_100_BILLION);
            return;
        }

        if (auctionState != ItemAuctionState.STARTED) {
            return;
        }

        if ((highestBid != null) && (newBid < highestBid.getLastBid())) {
            player.sendPacket(SystemMessageId.YOUR_BID_MUST_BE_HIGHER_THAN_THE_CURRENT_HIGHEST_BID);
            return;
        }

        final int playerObjId = player.getObjectId();
        ItemAuctionBid bid = findBid(playerObjId);
        if (bid == null) {
            if (!reduceItemCount(player, newBid)) {
                player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA_FOR_THIS_BID);
                return;
            }

            bid = new ItemAuctionBid(playerObjId, newBid);
            auctionBids.add(bid);
        } else {
            if (!bid.isCanceled()) {
                if (newBid < bid.getLastBid()) // just another check
                {
                    player.sendPacket(SystemMessageId.YOUR_BID_MUST_BE_HIGHER_THAN_THE_CURRENT_HIGHEST_BID);
                    return;
                }

                if (!reduceItemCount(player, newBid - bid.getLastBid())) {
                    player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA_FOR_THIS_BID);
                    return;
                }
            } else if (!reduceItemCount(player, newBid)) {
                player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA_FOR_THIS_BID);
                return;
            }

            bid.setLastBid(newBid);
        }

        onPlayerBid(player, bid);
        updatePlayerBid(bid, false);

        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_SUBMITTED_A_BID_FOR_THE_AUCTION_OF_S1);
        sm.addLong(newBid);
        player.sendPacket(sm);
    }

    private void onPlayerBid(Player player, ItemAuctionBid bid) {
        if (highestBid == null) {
            highestBid = bid;
        } else if (highestBid.getLastBid() < bid.getLastBid()) {
            final Player old = highestBid.getPlayer();
            if (old != null) {
                old.sendPacket(SystemMessageId.YOU_HAVE_BEEN_OUTBID);
            }

            highestBid = bid;
        }

        if ((endingTime - ClockBridge.wallMillis()) <= (1000 * 60 * 10)) {
            switch (auctionEndingExtendState) {
                case INITIAL: {
                    auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_5_MIN;
                    endingTime += ENDING_TIME_EXTEND_5;
                    broadcastToAllBiddersLater(new SystemMessage(
                            SystemMessageId.BIDDER_EXISTS_THE_AUCTION_TIME_HAS_BEEN_EXTENDED_BY_5_MINUTES));
                    break;
                }
                case EXTEND_BY_5_MIN: {
                    if (getAndSetLastBidPlayerObjectId(player.getObjectId()) != player.getObjectId()) {
                        auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_3_MIN;
                        endingTime += ENDING_TIME_EXTEND_3;
                        broadcastToAllBiddersLater(new SystemMessage(
                                SystemMessageId.BIDDER_EXISTS_AUCTION_TIME_HAS_BEEN_EXTENDED_BY_3_MINUTES));
                    }
                    break;
                }
                case EXTEND_BY_3_MIN: {
                    if ((GeneralConfig.ALT_ITEM_AUCTION_TIME_EXTENDS_ON_BID > 0)
                            && (getAndSetLastBidPlayerObjectId(player.getObjectId()) != player.getObjectId())) {
                        auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A;
                        endingTime += GeneralConfig.ALT_ITEM_AUCTION_TIME_EXTENDS_ON_BID;
                    }
                    break;
                }
                case EXTEND_BY_CONFIG_PHASE_A: {
                    if ((getAndSetLastBidPlayerObjectId(player.getObjectId()) != player.getObjectId())
                            && (scheduledAuctionEndingExtendState == ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_B)) {
                        auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_B;
                        endingTime += GeneralConfig.ALT_ITEM_AUCTION_TIME_EXTENDS_ON_BID;
                    }
                    break;
                }
                case EXTEND_BY_CONFIG_PHASE_B: {
                    if ((getAndSetLastBidPlayerObjectId(player.getObjectId()) != player.getObjectId())
                            && (scheduledAuctionEndingExtendState == ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A)) {
                        endingTime += GeneralConfig.ALT_ITEM_AUCTION_TIME_EXTENDS_ON_BID;
                        auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A;
                    }
                }
            }
        }
    }

    // Deferred, so the notice reaches the bidder after the confirmation of his own bid.
    private void broadcastToAllBiddersLater(ServerPacket packet) {
        WorldPartition.loop().execute(() -> broadcastToAllBidders(packet));
    }

    public void broadcastToAllBidders(ServerPacket packet) {
        WorldPartition.checkOwner();
        try (Snapshot<ItemAuctionBid> walk = ScratchBuffers.snapshot(auctionBids)) {
            for (int i = walk.size(); i-- > 0; ) {
                final ItemAuctionBid bid = walk.get(i);
                if (bid != null) {
                    final Player player = bid.getPlayer();
                    if (player != null) {
                        player.sendPacket(packet);
                    }
                }
            }
        }
    }

    public boolean cancelBid(Player player) {
        WorldPartition.checkOwner();
        if (player == null) {
            throw new NullPointerException();
        }

        switch (auctionState) {
            case CREATED: {
                return false;
            }
            case FINISHED: {
                if (startingTime
                        < (ClockBridge.wallMillis()
                                - TimeUnit.MILLISECONDS.convert(
                                        GeneralConfig.ALT_ITEM_AUCTION_EXPIRED_AFTER, TimeUnit.DAYS))) {
                    return false;
                }
                break;
            }
        }

        if (highestBid == null) {
            return false;
        }

        final ItemAuctionBid bid = findBid(player.getObjectId());
        if (bid == null) {
            return false;
        }

        if (bid.getPlayerObjId() == highestBid.getPlayerObjId()) {
            // Can't return winning bid.
            if (auctionState == ItemAuctionState.FINISHED) {
                return false;
            }

            player.sendPacket(SystemMessageId.YOU_CURRENTLY_HAVE_THE_HIGHEST_BID_BUT_THE_RESERVE_HAS_NOT_BEEN_MET);
            return true;
        }

        if (bid.isCanceled()) {
            return false;
        }

        increaseItemCount(player, bid.getLastBid());
        bid.cancelBid();

        // Delete bid from database if auction already finished.
        updatePlayerBid(bid, auctionState == ItemAuctionState.FINISHED);

        player.sendPacket(SystemMessageId.YOU_HAVE_CANCELED_YOUR_BID);
        return true;
    }

    public void clearCanceledBids() {
        WorldPartition.checkOwner();
        if (auctionState != ItemAuctionState.FINISHED) {
            throw new IllegalStateException("Attempt to clear canceled bids for non-finished auction");
        }

        try (Snapshot<ItemAuctionBid> walk = ScratchBuffers.snapshot(auctionBids)) {
            for (ItemAuctionBid bid : walk) {
                if ((bid != null) && bid.isCanceled()) {
                    updatePlayerBid(bid, true);
                }
            }
        }
    }

    private boolean reduceItemCount(Player player, long count) {
        if (player.reduceAdena(ItemProcessType.FEE, count, player, true)) {
            return true;
        }

        player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA_FOR_THIS_BID);
        return false;
    }

    private void increaseItemCount(Player player, long count) {
        player.addAdena(ItemProcessType.REFUND, count, player, true);
    }

    /**
     * Returns the last bid for the given player or -1 if he did not made one yet.
     * @param player The player that made the bid
     * @return The last bid the player made or -1
     */
    public long getLastBid(Player player) {
        WorldPartition.checkOwner();
        final ItemAuctionBid bid = findBid(player.getObjectId());
        return bid != null ? bid.getLastBid() : -1L;
    }

    public ItemAuctionBid getBidFor(int playerObjId) {
        WorldPartition.checkOwner();
        return findBid(playerObjId);
    }

    private ItemAuctionBid findBid(int playerObjId) {
        for (int i = auctionBids.size(); i-- > 0; ) {
            final ItemAuctionBid bid = auctionBids.get(i);
            if ((bid != null) && (bid.getPlayerObjId() == playerObjId)) {
                return bid;
            }
        }

        return null;
    }
}
