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
import static app.l2nx.highfive.entity.itemcontainer.Inventory.MAX_ADENA;

import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClanHallAuction {
    @Getter
    private int id = 0;

    @Getter
    private long endDate;

    @Getter
    private int highestBidderId = 0;

    @Getter
    private String highestBidderName = "";

    @Getter
    private long highestBidderMaxBid = 0;

    @Getter
    private int itemId = 0;

    @Getter
    private String itemName = "";

    @Getter
    private int itemObjectId = 0;

    @Getter
    private final long itemQuantity = 0;

    @Getter
    private String itemType = "";

    @Getter
    private int sellerId = 0;

    @Getter
    private String sellerClanName = "";

    @Getter
    private String sellerName = "";

    @Getter
    private long currentBid = 0;

    @Getter
    private long startingBid = 0;

    @Getter
    private final Map<Integer, Bidder> bidders = new ConcurrentHashMap<>();

    private static final String[] ItemTypeName = {"ClanHall"};

    public static class Bidder {
        @Getter
        private final String name; // TODO replace with objId

        @Getter
        private final String clanName;

        @Getter
        @Setter
        private long bid;

        @Getter
        private final Calendar timeBid;

        public Bidder(String name, String clanName, long bid, long timeBid) {
            this.name = name;
            this.clanName = clanName;
            this.bid = bid;
            this.timeBid = Calendar.getInstance();
            this.timeBid.setTimeInMillis(timeBid);
        }

        public void setTimeBid(long timeBid) {
            this.timeBid.setTimeInMillis(timeBid);
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
        endDate = System.currentTimeMillis() + delay;
        this.itemId = itemId;
        itemName = name;
        itemType = "ClanHall";
        sellerId = clan.getLeaderId();
        sellerName = clan.getLeaderName();
        sellerClanName = clan.getName();
        startingBid = bid;
    }

    /** Load auctions */
    private void load() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("Select * from auction where id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    currentBid = rs.getLong("currentBid");
                    endDate = rs.getLong("endDate");
                    itemId = rs.getInt("itemId");
                    itemName = rs.getString("itemName");
                    itemObjectId = rs.getInt("itemObjectId");
                    itemType = rs.getString("itemType");
                    sellerId = rs.getInt("sellerId");
                    sellerClanName = rs.getString("sellerClanName");
                    sellerName = rs.getString("sellerName");
                    startingBid = rs.getLong("startingBid");
                }
            }

            loadBid();
        } catch (Exception e) {
            log.warn("Exception: Auction.load(): {}", e.getMessage(), e);
        }
    }

    /** Load bidders **/
    private void loadBid() {
        highestBidderId = 0;
        highestBidderName = "";
        highestBidderMaxBid = 0;

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT bidderId, bidderName, maxBid, clan_name, time_bid FROM auction_bid WHERE auctionId = ? ORDER BY maxBid DESC")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.isFirst()) {
                        highestBidderId = rs.getInt("bidderId");
                        highestBidderName = rs.getString("bidderName");
                        highestBidderMaxBid = rs.getLong("maxBid");
                    }

                    bidders.put(
                            rs.getInt("bidderId"),
                            new Bidder(
                                    rs.getString("bidderName"),
                                    rs.getString("clan_name"),
                                    rs.getLong("maxBid"),
                                    rs.getLong("time_bid")));
                }
            }
        } catch (Exception e) {
            log.warn("Exception: Auction.loadBid(): {}", e.getMessage(), e);
        }
    }

    /** Task Manage */
    private void startAutoTask() {
        final long currentTime = System.currentTimeMillis();
        long taskDelay = 0;
        if (endDate <= currentTime) {
            endDate = currentTime + (7 * 24 * 3600000);
            saveAuctionDate();
        } else {
            taskDelay = endDate - currentTime;
        }

        ThreadPool.schedule(new AutoEndTask(), taskDelay);
    }

    public static String getItemTypeName(AuctionItemType value) {
        return ItemTypeName[value.ordinal()];
    }

    /** Save Auction Data End */
    private void saveAuctionDate() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("Update auction set endDate = ? where id = ?")) {
            ps.setLong(1, endDate);
            ps.setInt(2, id);
            ps.execute();
        } catch (Exception e) {
            log.error("Exception: saveAuctionDate(): {}", e.getMessage(), e);
        }
    }

    /**
     * Set a bid
     * @param bidder
     * @param bid
     */
    public synchronized void setBid(Player bidder, long bid) {
        final long requiredAdena =
                highestBidderName.equals(bidder.getClan().getLeaderName()) ? bid - highestBidderMaxBid : bid;
        if ((((highestBidderId > 0) && (bid > highestBidderMaxBid)) || ((highestBidderId == 0) && (bid >= startingBid)))
                && takeItem(bidder, requiredAdena)) {
            updateInDB(bidder, bid);
            bidder.getClan().setAuctionBiddedAt(id, true);
            return;
        }

        if ((bid < startingBid) || (bid <= highestBidderMaxBid)) {
            bidder.sendPacket(SystemMessageId.YOUR_BID_PRICE_MUST_BE_HIGHER_THAN_THE_MINIMUM_PRICE_CURRENTLY_BEING_BID);
        }
    }

    /**
     * Returns the item to the clan warehouse.
     * @param clanName the clan name
     * @param quantity the Adena value
     * @param penalty if {@code true} fees are applied
     */
    private void returnItem(String clanName, long quantity, boolean penalty) {
        long amount = quantity;
        if (penalty) {
            amount *= 0.9; // take 10% tax fee if needed
        }

        final Clan clan = ClanTable.getInstance().getClanByName(clanName);
        if (clan == null) {
            log.warn("Clan {} doesn't exist!", clanName);
            return;
        }

        final ItemContainer cwh = clan.getWarehouse();
        if (cwh == null) {
            log.warn("There has been a problem with {}'s clan warehouse!", clanName);
            return;
        }

        // avoid overflow on return
        final long limit = MAX_ADENA - cwh.getAdena();
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
     */
    private void updateInDB(Player bidder, long bid) {
        try (Connection con = DatabaseFactory.getConnection()) {
            if (bidders.get(bidder.getClanId()) != null) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE auction_bid SET bidderId=?, bidderName=?, maxBid=?, time_bid=? WHERE auctionId=? AND bidderId=?")) {
                    ps.setInt(1, bidder.getClanId());
                    ps.setString(2, bidder.getClan().getLeaderName());
                    ps.setLong(3, bid);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.setInt(5, id);
                    ps.setInt(6, bidder.getClanId());
                    ps.execute();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO auction_bid (id, auctionId, bidderId, bidderName, maxBid, clan_name, time_bid) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, IdManager.getInstance().getNextId());
                    ps.setInt(2, id);
                    ps.setInt(3, bidder.getClanId());
                    ps.setString(4, bidder.getName());
                    ps.setLong(5, bid);
                    ps.setString(6, bidder.getClan().getName());
                    ps.setLong(7, System.currentTimeMillis());
                    ps.execute();
                }

                if (World.getPlayer(highestBidderName) != null) {
                    World.getPlayer(highestBidderName).sendMessage("You have been out bidded");
                }
            }

            final long currentTime = System.currentTimeMillis();
            highestBidderId = bidder.getClanId();
            highestBidderMaxBid = bid;
            highestBidderName = bidder.getClan().getLeaderName();
            if (bidders.get(highestBidderId) == null) {
                bidders.put(
                        highestBidderId,
                        new Bidder(highestBidderName, bidder.getClan().getName(), bid, currentTime));
            } else {
                bidders.get(highestBidderId).setBid(bid);
                bidders.get(highestBidderId).setTimeBid(currentTime);
            }

            bidder.sendPacket(SystemMessageId.YOUR_BID_HAS_BEEN_SUCCESSFULLY_PLACED);
        } catch (Exception e) {
            log.error("Exception: Auction.updateInDB(Player bidder, int bid): {}", e.getMessage(), e);
        }
    }

    /** Remove bids */
    private void removeBids() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM auction_bid WHERE auctionId=?")) {
            ps.setInt(1, id);
            ps.execute();
        } catch (Exception e) {
            log.error("Exception: Auction.deleteFromDB(): {}", e.getMessage(), e);
        }

        for (Bidder b : bidders.values()) {
            if (ClanTable.getInstance().getClanByName(b.getClanName()).getHideoutId() == 0) {
                returnItem(b.getClanName(), b.getBid(), true); // 10 % tax
            } else if (World.getPlayer(b.getName()) != null) {
                World.getPlayer(b.getName()).sendMessage("Congratulation you have won ClanHall!");
            }

            ClanTable.getInstance().getClanByName(b.getClanName()).setAuctionBiddedAt(0, true);
        }

        bidders.clear();
    }

    /** Remove auctions */
    public void deleteAuctionFromDB() {
        ClanHallAuctionManager.getInstance().getAuctions().remove(this);
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM auction WHERE itemId=?")) {
            ps.setInt(1, itemId);
            ps.execute();
        } catch (Exception e) {
            log.error("Exception: Auction.deleteFromDB(): {}", e.getMessage(), e);
        }
    }

    /** End of auction */
    public void endAuction() {
        if (ClanHallTable.getInstance().loaded()) {
            if ((highestBidderId == 0) && (sellerId == 0)) {
                startAutoTask();
                return;
            }

            if ((highestBidderId == 0) && (sellerId > 0)) {
                /**
                 * If seller haven't sell ClanHall, auction removed, THIS MUST BE CONFIRMED
                 */
                final int aucId = ClanHallAuctionManager.getInstance().getAuctionIndex(id);
                ClanHallAuctionManager.getInstance().getAuctions().remove(aucId);
                return;
            }

            if (sellerId > 0) {
                returnItem(sellerClanName, highestBidderMaxBid, true);
                returnItem(
                        sellerClanName,
                        ClanHallTable.getInstance()
                                .getAuctionableHallById(itemId)
                                .getLease(),
                        false);
            }

            deleteAuctionFromDB();
            final Clan clan = ClanTable.getInstance()
                    .getClanByName(bidders.get(highestBidderId).getClanName());
            bidders.remove(highestBidderId);
            clan.setAuctionBiddedAt(0, true);
            removeBids();
            ClanHallTable.getInstance().setOwner(itemId, clan);
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
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("DELETE FROM auction_bid WHERE auctionId=? AND bidderId=?")) {
            ps.setInt(1, id);
            ps.setInt(2, bidder);
            ps.execute();
        } catch (Exception e) {
            log.error("Exception: Auction.cancelBid(String bidder): {}", e.getMessage(), e);
        }

        returnItem(bidders.get(bidder).getClanName(), bidders.get(bidder).getBid(), true);
        ClanTable.getInstance().getClanByName(bidders.get(bidder).getClanName()).setAuctionBiddedAt(0, true);
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
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO auction (id, sellerId, sellerName, sellerClanName, itemType, itemId, itemObjectId, itemName, itemQuantity, startingBid, currentBid, endDate) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, id);
            ps.setInt(2, sellerId);
            ps.setString(3, sellerName);
            ps.setString(4, sellerClanName);
            ps.setString(5, itemType);
            ps.setInt(6, itemId);
            ps.setInt(7, itemObjectId);
            ps.setString(8, itemName);
            ps.setLong(9, itemQuantity);
            ps.setLong(10, startingBid);
            ps.setLong(11, currentBid);
            ps.setLong(12, endDate);
            ps.execute();
            ps.close();
        } catch (Exception e) {
            log.error("Exception: Auction.load(): {}", e.getMessage(), e);
        }
    }
}
