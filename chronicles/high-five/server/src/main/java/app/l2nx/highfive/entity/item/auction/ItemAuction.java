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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.auction.model.ItemAuctionBidRow;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Forsaiken
 */
public class ItemAuction {

    private static final long ENDING_TIME_EXTEND_5 = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
    private static final long ENDING_TIME_EXTEND_3 = TimeUnit.MILLISECONDS.convert(3, TimeUnit.MINUTES);

    @Getter
    private final int auctionId;

    @Getter
    private final int instanceId;

    @Getter
    private final long startingTime;

    @Getter
    private volatile long endingTime;

    private final AuctionItem auctionItem;
    private final List<ItemAuctionBid> auctionBids;
    private final Object auctionStateLock;

    private ItemAuctionState auctionState;

    @Getter
    @Setter
    private ItemAuctionExtendState scheduledAuctionEndingExtendState;

    @Getter
    private ItemAuctionExtendState auctionEndingExtendState;

    @Getter
    private final ItemInfo itemInfo;

    @Getter
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
        auctionStateLock = new Object();
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

    public ItemAuctionState getAuctionState() {
        final ItemAuctionState auctionState;

        synchronized (auctionStateLock) {
            auctionState = this.auctionState;
        }

        return auctionState;
    }

    public boolean setAuctionState(ItemAuctionState expected, ItemAuctionState wanted) {
        synchronized (auctionStateLock) {
            if (auctionState != expected) {
                return false;
            }

            auctionState = wanted;
            storeMe();
            return true;
        }
    }

    public Item createNewItemInstance() {
        return auctionItem.createNewItemInstance();
    }

    public long getAuctionInitBid() {
        return auctionItem.getAuctionInitBid();
    }

    public long getStartingTimeRemaining() {
        return Math.max(endingTime - ClockBridge.wallMillis(), 0);
    }

    public long getFinishingTimeRemaining() {
        return Math.max(endingTime - ClockBridge.wallMillis(), 0);
    }

    /**
     * Stores the state; the auction itself is stored when it is created.
     */
    public void storeMe() {
        ItemAuctionManager.getInstance().updateAuctionState(auctionId, auctionState);
    }

    public int getAndSetLastBidPlayerObjectId(int playerObjId) {
        final int lastBid = lastBidPlayerObjId;
        lastBidPlayerObjId = playerObjId;
        return lastBid;
    }

    private void updatePlayerBid(ItemAuctionBid bid, boolean delete) {
        updatePlayerBidInternal(bid, delete);
    }

    private void updatePlayerBidInternal(ItemAuctionBid bid, boolean delete) {
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

        if (getAuctionState() != ItemAuctionState.STARTED) {
            return;
        }

        final int playerObjId = player.getObjectId();

        synchronized (auctionBids) {
            if ((highestBid != null) && (newBid < highestBid.getLastBid())) {
                player.sendPacket(SystemMessageId.YOUR_BID_MUST_BE_HIGHER_THAN_THE_CURRENT_HIGHEST_BID);
                return;
            }

            ItemAuctionBid bid = getBidFor(playerObjId);
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

        if ((endingTime - ClockBridge.wallMillis()) <= (1000 * 60 * 10)) // 10 minutes
        {
            switch (auctionEndingExtendState) {
                case INITIAL: {
                    auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_5_MIN;
                    endingTime += ENDING_TIME_EXTEND_5;
                    broadcastToAllBidders(new SystemMessage(
                            SystemMessageId.BIDDER_EXISTS_THE_AUCTION_TIME_HAS_BEEN_EXTENDED_BY_5_MINUTES));
                    break;
                }
                case EXTEND_BY_5_MIN: {
                    if (getAndSetLastBidPlayerObjectId(player.getObjectId()) != player.getObjectId()) {
                        auctionEndingExtendState = ItemAuctionExtendState.EXTEND_BY_3_MIN;
                        endingTime += ENDING_TIME_EXTEND_3;
                        broadcastToAllBidders(new SystemMessage(
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

    public void broadcastToAllBidders(ServerPacket packet) {
        ThreadPool.execute(() -> broadcastToAllBiddersInternal(packet));
    }

    public void broadcastToAllBiddersInternal(ServerPacket packet) {
        for (int i = auctionBids.size(); i-- > 0; ) {
            final ItemAuctionBid bid = auctionBids.get(i);
            if (bid != null) {
                final Player player = bid.getPlayer();
                if (player != null) {
                    player.sendPacket(packet);
                }
            }
        }
    }

    public boolean cancelBid(Player player) {
        if (player == null) {
            throw new NullPointerException();
        }

        switch (getAuctionState()) {
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

        final int playerObjId = player.getObjectId();

        synchronized (auctionBids) {
            if (highestBid == null) {
                return false;
            }

            final int bidIndex = getBidIndexFor(playerObjId);
            if (bidIndex == -1) {
                return false;
            }

            final ItemAuctionBid bid = auctionBids.get(bidIndex);
            if (bid.getPlayerObjId() == highestBid.getPlayerObjId()) {
                // Can't return winning bid.
                if (getAuctionState() == ItemAuctionState.FINISHED) {
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
            updatePlayerBid(bid, getAuctionState() == ItemAuctionState.FINISHED);

            player.sendPacket(SystemMessageId.YOU_HAVE_CANCELED_YOUR_BID);
        }

        return true;
    }

    public void clearCanceledBids() {
        if (getAuctionState() != ItemAuctionState.FINISHED) {
            throw new IllegalStateException("Attempt to clear canceled bids for non-finished auction");
        }

        synchronized (auctionBids) {
            for (ItemAuctionBid bid : auctionBids) {
                if ((bid == null) || !bid.isCanceled()) {
                    continue;
                }

                updatePlayerBid(bid, true);
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
        final ItemAuctionBid bid = getBidFor(player.getObjectId());
        return bid != null ? bid.getLastBid() : -1L;
    }

    public ItemAuctionBid getBidFor(int playerObjId) {
        final int index = getBidIndexFor(playerObjId);
        return index != -1 ? auctionBids.get(index) : null;
    }

    private int getBidIndexFor(int playerObjId) {
        for (int i = auctionBids.size(); i-- > 0; ) {
            final ItemAuctionBid bid = auctionBids.get(i);
            if ((bid != null) && (bid.getPlayerObjId() == playerObjId)) {
                return i;
            }
        }

        return -1;
    }
}
