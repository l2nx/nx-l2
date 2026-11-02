/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.residences;

import static app.l2nx.highfive.entity.itemcontainer.Inventory.ADENA_ID;
import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.ClanHallTemplate;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.ClanHallData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.auction.model.ClanHallAuctionRow;
import app.l2nx.highfive.repository.auction.model.ClanHallBid;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class ClanHallAuction {
    /** NPC auction length, also the extension when an auction ends without a bid. */
    public static final long AUCTION_LENGTH_MS = 7 * 24 * 3600000L;

    private static final String NPC_SELLER_NAME = "NPC";
    private static final String NPC_SELLER_CLAN_NAME = "NPC Clan";

    private int id = 0;

    private long endDate;

    private int highestBidderId = 0;

    private long highestBidderMaxBid = 0;

    private String itemName;

    /** {@code 0} for the NPC auction of a free hall. */
    private int sellerClanId = 0;

    private long currentBid = 0;

    private long startingBid = 0;

    // By clan id in bid order, the stored bids first, highest first: the bidder list shows them so.
    private final Int2ObjectLinkedOpenHashMap<Bidder> bidders = new Int2ObjectLinkedOpenHashMap<>();

    private ScheduledFuture<?> endTask;

    private boolean cancelled = false;

    @SingleWriter
    public static class Bidder {
        private final int clanId;

        private long bid;

        private long timeBid;

        public Bidder(int clanId, long bid, long timeBid) {
            this.clanId = clanId;
            this.bid = bid;
            this.timeBid = timeBid;
        }

        public int getClanId() {
            WorldPartition.checkOwner();
            return clanId;
        }

        public long getBid() {
            WorldPartition.checkOwner();
            return bid;
        }

        public void setBid(long bid) {
            WorldPartition.checkOwner();
            this.bid = bid;
        }

        public long getTimeBid() {
            WorldPartition.checkOwner();
            return timeBid;
        }

        public void setTimeBid(long timeBid) {
            WorldPartition.checkOwner();
            this.timeBid = timeBid;
        }

        /**
         * @return the leader of the bidding clan
         */
        public String getName() {
            WorldPartition.checkOwner();
            final Clan clan = ClanTable.getInstance().getClan(clanId);
            return clan == null ? null : clan.getLeaderName();
        }

        public String getClanName() {
            WorldPartition.checkOwner();
            final Clan clan = ClanTable.getInstance().getClan(clanId);
            return clan == null ? null : clan.getName();
        }
    }

    public class AutoEndTask implements Runnable {
        @Override
        public void run() {
            try {
                endAuction();
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    public ClanHallAuction(int auctionId) {
        id = auctionId;
        load();
        startAutoTask();
    }

    public ClanHallAuction(int itemId, Clan clan, long delay, long bid, String name) {
        id = itemId;
        endDate = ClockBridge.wallMillis() + delay;
        itemName = name;
        sellerClanId = clan.getId();
        startingBid = bid;
    }

    private void load() {
        ClanHallAuctionManager.getInstance().findAuction(id).ifPresent(auction -> {
            currentBid = auction.currentBid();
            endDate = epochMillisOrZero(auction.endDate());
            sellerClanId = auction.sellerClanId();
            startingBid = auction.startingBid();
        });

        final ClanHallTemplate hall = ClanHallData.getInstance().getClanHall(id);
        itemName = hall == null ? null : hall.name();
        loadBid();
    }

    private void loadBid() {
        highestBidderId = 0;
        highestBidderMaxBid = 0;

        boolean highest = true;
        for (ClanHallBid row : ClanHallAuctionManager.getInstance().findBids(id)) {
            if (highest) {
                highestBidderId = row.clanId();
                highestBidderMaxBid = row.bid();
                highest = false;
            }

            bidders.put(row.clanId(), new Bidder(row.clanId(), row.bid(), epochMillisOrZero(row.timeBid())));
        }
    }

    private void startAutoTask() {
        final long currentTime = ClockBridge.wallMillis();
        long taskDelay = 0;
        if (endDate <= currentTime) {
            endDate = currentTime + AUCTION_LENGTH_MS;
            saveAuctionDate();
        } else {
            taskDelay = endDate - currentTime;
        }

        endTask = WorldPartition.loop().schedule(new AutoEndTask(), taskDelay, TimeUnit.MILLISECONDS);
    }

    private void saveAuctionDate() {
        ClanHallAuctionManager.getInstance().saveAuctionEndDate(id, endDate);
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    /**
     * @return the item id of the auctioned clan hall, which is the clan hall id
     */
    public int getItemId() {
        WorldPartition.checkOwner();
        return id;
    }

    public long getEndDate() {
        WorldPartition.checkOwner();
        return endDate;
    }

    public int getHighestBidderId() {
        WorldPartition.checkOwner();
        return highestBidderId;
    }

    public long getHighestBidderMaxBid() {
        WorldPartition.checkOwner();
        return highestBidderMaxBid;
    }

    public String getItemName() {
        WorldPartition.checkOwner();
        return itemName;
    }

    public int getSellerClanId() {
        WorldPartition.checkOwner();
        return sellerClanId;
    }

    public long getCurrentBid() {
        WorldPartition.checkOwner();
        return currentBid;
    }

    public long getStartingBid() {
        WorldPartition.checkOwner();
        return startingBid;
    }

    public String getSellerName() {
        WorldPartition.checkOwner();
        final Clan seller = ClanTable.getInstance().getClan(sellerClanId);
        return seller == null ? NPC_SELLER_NAME : seller.getLeaderName();
    }

    public String getSellerClanName() {
        WorldPartition.checkOwner();
        final Clan seller = ClanTable.getInstance().getClan(sellerClanId);
        return seller == null ? NPC_SELLER_CLAN_NAME : seller.getName();
    }

    public List<Bidder> getBidders() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(bidders.values()));
    }

    /**
     * @return the bidder of the clan, {@code null} when the clan has not bid
     */
    public Bidder getBidder(int clanId) {
        WorldPartition.checkOwner();
        return bidders.get(clanId);
    }

    public int getBidderCount() {
        WorldPartition.checkOwner();
        return bidders.size();
    }

    public void setBid(Player bidder, long bid) {
        WorldPartition.checkOwner();
        final long requiredAdena = highestBidderId == bidder.getClanId() ? bid - highestBidderMaxBid : bid;
        if ((((highestBidderId > 0) && (bid > highestBidderMaxBid)) || ((highestBidderId == 0) && (bid >= startingBid)))
                && takeItem(bidder, requiredAdena)) {
            if (updateInDB(bidder, bid)) {
                bidder.getClan().setAuctionBiddedAt(id, true);
            } else {
                returnItem(bidder.getClan(), requiredAdena, false);
            }
            return;
        }

        if ((bid < startingBid) || (bid <= highestBidderMaxBid)) {
            bidder.sendPacket(SystemMessageId.YOUR_BID_PRICE_MUST_BE_HIGHER_THAN_THE_MINIMUM_PRICE_CURRENTLY_BEING_BID);
        }
    }

    /** Refunds Adena to the clan warehouse; {@code penalty} withholds 10%. */
    private void returnItem(Clan clan, long quantity, boolean penalty) {
        long amount = quantity;
        if (penalty) {
            amount *= 0.9;
        }

        if (clan == null) {
            log.warn("Clan doesn't exist!");
            return;
        }

        final ItemContainer cwh = clan.getWarehouse();
        if (cwh == null) {
            log.warn("There has been a problem with {}'s clan warehouse!", clan.getName());
            return;
        }

        final long limit = PlayerConfig.MAX_ADENA - cwh.getAdena();
        amount = Math.min(amount, limit);
        cwh.addItem(ItemProcessType.REFUND, ADENA_ID, amount, null, null);
    }

    private boolean takeItem(Player bidder, long quantity) {
        if ((bidder.getClan() != null) && (bidder.getClan().getWarehouse().getAdena() >= quantity)) {
            bidder.getClan()
                    .getWarehouse()
                    .destroyItemByItemId(ItemProcessType.BUY, ADENA_ID, quantity, bidder, bidder);
            return true;
        }

        bidder.sendPacket(SystemMessageId.THERE_IS_NOT_ENOUGH_ADENA_IN_THE_CLAN_HALL_WAREHOUSE);
        return false;
    }

    /** @return {@code false} when the bid could not be stored, so the auction is left as it was */
    private boolean updateInDB(Player bidder, long bid) {
        final long currentTime = ClockBridge.wallMillis();
        final int clanId = bidder.getClanId();
        if (!ClanHallAuctionManager.getInstance()
                .saveBid(
                        id,
                        ClanHallBid.builder()
                                .clanId(clanId)
                                .bid(bid)
                                .timeBid(instantOrNull(currentTime))
                                .build())) {
            return false;
        }

        final Bidder existing = bidders.get(clanId);
        if (existing == null) {
            final Clan outbid = ClanTable.getInstance().getClan(highestBidderId);
            final Player outbidLeader =
                    outbid == null ? null : outbid.getLeader().getPlayer();
            if (outbidLeader != null) {
                outbidLeader.sendMessage("You have been out bidded");
            }
        }

        highestBidderId = clanId;
        highestBidderMaxBid = bid;
        if (existing == null) {
            bidders.put(clanId, new Bidder(clanId, bid, currentTime));
        } else {
            existing.setBid(bid);
            existing.setTimeBid(currentTime);
        }

        bidder.sendPacket(SystemMessageId.YOUR_BID_HAS_BEEN_SUCCESSFULLY_PLACED);
        return true;
    }

    private void removeBids() {
        try (Snapshot<Bidder> walk = ScratchBuffers.snapshot(bidders.values())) {
            for (Bidder b : walk) {
                final Clan clan = ClanTable.getInstance().getClan(b.clanId);
                if (clan == null) {
                    continue;
                }

                if (clan.getHideoutId() == 0) {
                    returnItem(clan, b.bid, true);
                } else if (clan.getLeader().getPlayer() != null) {
                    clan.getLeader().getPlayer().sendMessage("Congratulation you have won ClanHall!");
                }

                clan.setAuctionBiddedAt(0, true);
            }
        }

        bidders.clear();
    }

    public void deleteAuctionFromDB() {
        WorldPartition.checkOwner();
        ClanHallAuctionManager.getInstance().getAuctions().remove(this);
        ClanHallAuctionManager.getInstance().deleteAuction(id);
    }

    public void endAuction() {
        WorldPartition.checkOwner();
        if (cancelled) {
            return;
        }

        if (ClanHallTable.getInstance().loaded()) {
            if ((highestBidderId == 0) && (sellerClanId == 0)) {
                startAutoTask();
                return;
            }

            if ((highestBidderId == 0) && (sellerClanId > 0)) {
                // An unsold hall stays with its seller; the stored row goes too, or the sale returns after a restart.
                deleteAuctionFromDB();
                return;
            }

            if (sellerClanId > 0) {
                final Clan seller = ClanTable.getInstance().getClan(sellerClanId);
                returnItem(seller, highestBidderMaxBid, true);
                returnItem(
                        seller,
                        ClanHallTable.getInstance().getAuctionableHallById(id).getLease(),
                        false);
            }

            deleteAuctionFromDB();
            final Clan clan = ClanTable.getInstance().getClan(highestBidderId);
            bidders.remove(highestBidderId);
            clan.setAuctionBiddedAt(0, true);
            removeBids();
            ClanHallTable.getInstance().setOwner(id, clan);
        } else {
            endTask = WorldPartition.loop().schedule(new AutoEndTask(), 3000, TimeUnit.MILLISECONDS);
        }
    }

    public void cancelBid(int bidder) {
        WorldPartition.checkOwner();
        final Bidder cancelledBid = bidders.get(bidder);
        if (cancelledBid == null) {
            return;
        }

        ClanHallAuctionManager.getInstance().deleteBid(id, bidder);

        final Clan clan = ClanTable.getInstance().getClan(bidder);
        returnItem(clan, cancelledBid.bid, true);
        if (clan != null) {
            clan.setAuctionBiddedAt(0, true);
        }

        bidders.clear();
        loadBid();
    }

    public void cancelAuction() {
        WorldPartition.checkOwner();
        cancelled = true;
        if (endTask != null) {
            endTask.cancel(false);
        }

        deleteAuctionFromDB();
        removeBids();
    }

    public void confirmAuction() {
        WorldPartition.checkOwner();
        ClanHallAuctionManager.getInstance().getAuctions().add(this);
        ClanHallAuctionManager.getInstance()
                .saveAuction(ClanHallAuctionRow.builder()
                        .clanHallId(id)
                        .sellerClanId(sellerClanId)
                        .startingBid(startingBid)
                        .currentBid(currentBid)
                        .endDate(instantOrNull(endDate))
                        .build());
    }
}
