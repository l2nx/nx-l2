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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.ClanHallTemplate;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.ClanHallData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.repository.auction.model.ClanHallAuctionRow;
import app.l2nx.highfive.repository.auction.model.ClanHallBid;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClanHallAuction {
    /**
     * The length of an NPC auction, and of the extension an auction gets when it ends without a bid.
     */
    public static final long AUCTION_LENGTH_MS = 7 * 24 * 3600000L;

    private static final String NPC_SELLER_NAME = "NPC";
    private static final String NPC_SELLER_CLAN_NAME = "NPC Clan";

    @Getter
    private int id = 0;

    @Getter
    private long endDate;

    @Getter
    private int highestBidderId = 0;

    @Getter
    private long highestBidderMaxBid = 0;

    @Getter
    private String itemName;

    /**
     * The selling clan, {@code 0} for the NPC auction of a free hall.
     */
    @Getter
    private int sellerClanId = 0;

    @Getter
    private long currentBid = 0;

    @Getter
    private long startingBid = 0;

    @Getter
    private final Map<Integer, Bidder> bidders = new ConcurrentHashMap<>();

    public static class Bidder {
        @Getter
        private final int clanId;

        @Getter
        @Setter
        private long bid;

        @Getter
        private final Calendar timeBid;

        public Bidder(int clanId, long bid, long timeBid) {
            this.clanId = clanId;
            this.bid = bid;
            this.timeBid = ClockBridge.calendar();
            this.timeBid.setTimeInMillis(timeBid);
        }

        public void setTimeBid(long timeBid) {
            this.timeBid.setTimeInMillis(timeBid);
        }

        /**
         * @return the leader of the bidding clan
         */
        public String getName() {
            final Clan clan = ClanTable.getInstance().getClan(clanId);
            return clan == null ? null : clan.getLeaderName();
        }

        public String getClanName() {
            final Clan clan = ClanTable.getInstance().getClan(clanId);
            return clan == null ? null : clan.getName();
        }
    }

    /** Task Sheduler for endAuction */
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

    /**
     * Constructor
     * @param auctionId
     */
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

    /** Load auctions */
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

    /** Load bidders **/
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

    /** Task Manage */
    private void startAutoTask() {
        final long currentTime = ClockBridge.wallMillis();
        long taskDelay = 0;
        if (endDate <= currentTime) {
            endDate = currentTime + AUCTION_LENGTH_MS;
            saveAuctionDate();
        } else {
            taskDelay = endDate - currentTime;
        }

        ThreadPool.schedule(new AutoEndTask(), taskDelay);
    }

    /** Save Auction Data End */
    private void saveAuctionDate() {
        ClanHallAuctionManager.getInstance().saveAuctionEndDate(id, endDate);
    }

    /**
     * @return the item id of the auctioned clan hall, which is the clan hall id
     */
    public int getItemId() {
        return id;
    }

    public String getSellerName() {
        final Clan seller = ClanTable.getInstance().getClan(sellerClanId);
        return seller == null ? NPC_SELLER_NAME : seller.getLeaderName();
    }

    public String getSellerClanName() {
        final Clan seller = ClanTable.getInstance().getClan(sellerClanId);
        return seller == null ? NPC_SELLER_CLAN_NAME : seller.getName();
    }

    /**
     * Set a bid
     * @param bidder
     * @param bid
     */
    public synchronized void setBid(Player bidder, long bid) {
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

    /**
     * Returns the item to the clan warehouse.
     * @param quantity the Adena value
     * @param penalty if {@code true} fees are applied
     */
    private void returnItem(Clan clan, long quantity, boolean penalty) {
        long amount = quantity;
        if (penalty) {
            amount *= 0.9; // take 10% tax fee if needed
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

        // avoid overflow on return
        final long limit = PlayerConfig.MAX_ADENA - cwh.getAdena();
        amount = Math.min(amount, limit);
        cwh.addItem(ItemProcessType.REFUND, ADENA_ID, amount, null, null);
    }

    /**
     * Take Item in WHC
     * @param bidder
     * @param quantity
     * @return
     */
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

    /**
     * Update auction in DB
     * @param bidder
     * @param bid
     * @return {@code false} when the bid could not be stored, so the auction is left as it was
     */
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

        if (bidders.get(clanId) == null) {
            final Clan outbid = ClanTable.getInstance().getClan(highestBidderId);
            final Player outbidLeader =
                    outbid == null ? null : outbid.getLeader().getPlayer();
            if (outbidLeader != null) {
                outbidLeader.sendMessage("You have been out bidded");
            }
        }

        highestBidderId = clanId;
        highestBidderMaxBid = bid;
        if (bidders.get(clanId) == null) {
            bidders.put(clanId, new Bidder(clanId, bid, currentTime));
        } else {
            bidders.get(clanId).setBid(bid);
            bidders.get(clanId).setTimeBid(currentTime);
        }

        bidder.sendPacket(SystemMessageId.YOUR_BID_HAS_BEEN_SUCCESSFULLY_PLACED);
        return true;
    }

    /** Remove bids */
    private void removeBids() {
        for (Bidder b : bidders.values()) {
            final Clan clan = ClanTable.getInstance().getClan(b.getClanId());
            if (clan == null) {
                continue;
            }

            if (clan.getHideoutId() == 0) {
                returnItem(clan, b.getBid(), true); // 10 % tax
            } else if (clan.getLeader().getPlayer() != null) {
                clan.getLeader().getPlayer().sendMessage("Congratulation you have won ClanHall!");
            }

            clan.setAuctionBiddedAt(0, true);
        }

        bidders.clear();
    }

    /** Remove auctions */
    public void deleteAuctionFromDB() {
        ClanHallAuctionManager.getInstance().getAuctions().remove(this);
        ClanHallAuctionManager.getInstance().deleteAuction(id);
    }

    /** End of auction */
    public void endAuction() {
        if (ClanHallTable.getInstance().loaded()) {
            if ((highestBidderId == 0) && (sellerClanId == 0)) {
                startAutoTask();
                return;
            }

            if ((highestBidderId == 0) && (sellerClanId > 0)) {
                /**
                 * If seller haven't sell ClanHall, auction removed, THIS MUST BE CONFIRMED
                 */
                final int aucId = ClanHallAuctionManager.getInstance().getAuctionIndex(id);
                ClanHallAuctionManager.getInstance().getAuctions().remove(aucId);
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
            /** Task waiting ClanHallManager is loaded every 3s */
            ThreadPool.schedule(new AutoEndTask(), 3000);
        }
    }

    /**
     * Cancel bid
     * @param bidder
     */
    public synchronized void cancelBid(int bidder) {
        ClanHallAuctionManager.getInstance().deleteBid(id, bidder);

        final Clan clan = ClanTable.getInstance().getClan(bidder);
        returnItem(clan, bidders.get(bidder).getBid(), true);
        if (clan != null) {
            clan.setAuctionBiddedAt(0, true);
        }

        bidders.clear();
        loadBid();
    }

    /** Cancel auction */
    public void cancelAuction() {
        deleteAuctionFromDB();
        removeBids();
    }

    /** Confirm an auction */
    public void confirmAuction() {
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
