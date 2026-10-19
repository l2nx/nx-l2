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
package app.l2nx.highfive.entity.item.auction;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Slf4j
public class ItemAuctionInstance {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yy");

    private static final long START_TIME_SPACE = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
    private static final long FINISH_TIME_SPACE = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

    // SQL queries
    private static final String SELECT_AUCTION_ID_BY_INSTANCE_ID =
            "SELECT auctionId FROM item_auction WHERE instanceId = ?";
    private static final String SELECT_AUCTION_INFO =
            "SELECT auctionItemId, startingTime, endingTime, auctionStateId FROM item_auction WHERE auctionId = ? ";
    private static final String DELETE_AUCTION_INFO_BY_AUCTION_ID = "DELETE FROM item_auction WHERE auctionId = ?";
    private static final String DELETE_AUCTION_BID_INFO_BY_AUCTION_ID =
            "DELETE FROM item_auction_bid WHERE auctionId = ?";
    private static final String SELECT_PLAYERS_ID_BY_AUCTION_ID =
            "SELECT playerObjId, playerBid FROM item_auction_bid WHERE auctionId = ?";

    private final int instanceId;
    private final AtomicInteger auctionIds;
    private final Map<Integer, ItemAuction> auctions;
    private final List<AuctionItem> items;
    private final AuctionDateGenerator dateGenerator;

    @Getter
    private ItemAuction currentAuction;

    @Getter
    private ItemAuction nextAuction;

    private ScheduledFuture<?> stateTask;

    public ItemAuctionInstance(int instanceId, AtomicInteger auctionIds, Node node) throws Exception {
        this.instanceId = instanceId;
        this.auctionIds = auctionIds;
        auctions = new HashMap<>();
        items = new ArrayList<>();

        final NamedNodeMap nanode = node.getAttributes();
        final StatSet generatorConfig = new StatSet();
        for (int i = nanode.getLength(); i-- > 0; ) {
            final Node n = nanode.item(i);
            if (n != null) {
                generatorConfig.set(n.getNodeName(), n.getNodeValue());
            }
        }

        dateGenerator = new AuctionDateGenerator(generatorConfig);

        for (Node na = node.getFirstChild(); na != null; na = na.getNextSibling()) {
            try {
                if ("item".equalsIgnoreCase(na.getNodeName())) {
                    final NamedNodeMap naa = na.getAttributes();
                    final int auctionLength =
                            Integer.parseInt(naa.getNamedItem("auctionLength").getNodeValue());
                    final int itemId =
                            Integer.parseInt(naa.getNamedItem("itemId").getNodeValue());
                    if (auctionLength < 1) {
                        throw new IllegalArgumentException(
                                "auctionLength < 1 for instanceId: " + this.instanceId + ", itemId " + itemId);
                    }

                    final int auctionItemId =
                            Integer.parseInt(naa.getNamedItem("auctionItemId").getNodeValue());
                    final long auctionInitBid =
                            Integer.parseInt(naa.getNamedItem("auctionInitBid").getNodeValue());
                    final int itemCount =
                            Integer.parseInt(naa.getNamedItem("itemCount").getNodeValue());
                    final StatSet itemExtra = new StatSet();
                    final AuctionItem item =
                            new AuctionItem(auctionItemId, auctionLength, auctionInitBid, itemId, itemCount, itemExtra);

                    if (!item.checkItemExists()) {
                        throw new IllegalArgumentException("Item with id " + itemId + " not found");
                    }

                    for (AuctionItem tmp : items) {
                        if (tmp.getAuctionItemId() == auctionItemId) {
                            throw new IllegalArgumentException("Dublicated auction item id " + auctionItemId);
                        }
                    }

                    items.add(item);

                    for (Node nb = na.getFirstChild(); nb != null; nb = nb.getNextSibling()) {
                        if ("extra".equalsIgnoreCase(nb.getNodeName())) {
                            final NamedNodeMap nab = nb.getAttributes();
                            for (int i = nab.getLength(); i-- > 0; ) {
                                final Node n = nab.item(i);
                                if (n != null) {
                                    itemExtra.set(n.getNodeName(), n.getNodeValue());
                                }
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Failed loading auction item", e);
            }
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("No items defined");
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(SELECT_AUCTION_ID_BY_INSTANCE_ID)) {
            ps.setInt(1, this.instanceId);
            try (ResultSet rset = ps.executeQuery()) {
                while (rset.next()) {
                    final int auctionId = rset.getInt(1);
                    try {
                        final ItemAuction auction = loadAuction(auctionId);
                        if (auction != null) {
                            auctions.put(auctionId, auction);
                        } else {
                            ItemAuctionManager.deleteAuction(auctionId);
                        }
                    } catch (SQLException e) {
                        log.warn("Failed loading auction: {}", auctionId, e);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed loading auctions.", e);
            return;
        }

        log.info(
                "Loaded {} item(s) and registered {} auction(s) for instance {}.",
                items.size(),
                auctions.size(),
                this.instanceId);
        checkAndSetCurrentAndNextAuction();
    }

    public void shutdown() {
        if (stateTask != null) {
            stateTask.cancel(false);
        }
    }

    private AuctionItem getAuctionItem(int auctionItemId) {
        for (int i = items.size(); i-- > 0; ) {
            final AuctionItem item = items.get(i);
            if (item.getAuctionItemId() == auctionItemId) {
                return item;
            }
        }

        return null;
    }

    protected void checkAndSetCurrentAndNextAuction() {
        final ItemAuction[] auctions = this.auctions.values().toArray(new ItemAuction[0]);

        ItemAuction currentAuction = null;
        ItemAuction nextAuction = null;

        switch (auctions.length) {
            case 0: {
                nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
                break;
            }
            case 1: {
                switch (auctions[0].getAuctionState()) {
                    case CREATED: {
                        if (auctions[0].getStartingTime() < (System.currentTimeMillis() + START_TIME_SPACE)) {
                            currentAuction = auctions[0];
                            nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
                        } else {
                            nextAuction = auctions[0];
                        }
                        break;
                    }
                    case STARTED: {
                        currentAuction = auctions[0];
                        nextAuction = createAuction(Math.max(
                                currentAuction.getEndingTime() + FINISH_TIME_SPACE,
                                System.currentTimeMillis() + START_TIME_SPACE));
                        break;
                    }
                    case FINISHED: {
                        currentAuction = auctions[0];
                        nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException();
                    }
                }
                break;
            }
            default: {
                Arrays.sort(
                        auctions,
                        Comparator.comparingLong(ItemAuction::getStartingTime).reversed());

                // Just to make sure we won't skip any auction because of little different times.
                final long currentTime = System.currentTimeMillis();

                for (ItemAuction auction : auctions) {
                    if ((auction.getAuctionState() == ItemAuctionState.STARTED)
                            || (auction.getStartingTime() <= currentTime)) {
                        currentAuction = auction;
                        break;
                    }
                }

                for (ItemAuction auction : auctions) {
                    if ((auction.getStartingTime() > currentTime) && (currentAuction != auction)) {
                        nextAuction = auction;
                        break;
                    }
                }

                if (nextAuction == null) {
                    nextAuction = createAuction(System.currentTimeMillis() + START_TIME_SPACE);
                }
                break;
            }
        }

        this.auctions.put(nextAuction.getAuctionId(), nextAuction);

        this.currentAuction = currentAuction;
        this.nextAuction = nextAuction;

        if ((currentAuction != null) && (currentAuction.getAuctionState() != ItemAuctionState.FINISHED)) {
            if (currentAuction.getAuctionState() == ItemAuctionState.STARTED) {
                setStateTask(ThreadPool.schedule(
                        new ScheduleAuctionTask(currentAuction),
                        Math.max(currentAuction.getEndingTime() - System.currentTimeMillis(), 0)));
            } else {
                setStateTask(ThreadPool.schedule(
                        new ScheduleAuctionTask(currentAuction),
                        Math.max(currentAuction.getStartingTime() - System.currentTimeMillis(), 0)));
            }

            log.info("Schedule current auction {} for instance {}", currentAuction.getAuctionId(), instanceId);
        } else {
            setStateTask(ThreadPool.schedule(
                    new ScheduleAuctionTask(nextAuction),
                    Math.max(nextAuction.getStartingTime() - System.currentTimeMillis(), 0)));
            log.info(
                    "Schedule next auction {} on {} for instance {}",
                    nextAuction.getAuctionId(),
                    DATE_FORMAT.format(
                            Instant.ofEpochMilli(nextAuction.getStartingTime()).atZone(ZoneId.systemDefault())),
                    instanceId);
        }
    }

    public ItemAuction getAuction(int auctionId) {
        return auctions.get(auctionId);
    }

    public List<ItemAuction> getAuctionsByBidder(int bidderObjId) {
        final Collection<ItemAuction> auctions = getAuctions();
        final List<ItemAuction> stack = new ArrayList<>(auctions.size());
        for (ItemAuction auction : getAuctions()) {
            if ((auction.getAuctionState() != ItemAuctionState.CREATED) && (auction.getBidFor(bidderObjId) != null)) {
                stack.add(auction);
            }
        }

        return stack;
    }

    public Collection<ItemAuction> getAuctions() {
        final Collection<ItemAuction> auctions;

        synchronized (this.auctions) {
            auctions = this.auctions.values();
        }

        return auctions;
    }

    private class ScheduleAuctionTask implements Runnable {
        private final ItemAuction auction;

        public ScheduleAuctionTask(ItemAuction auction) {
            this.auction = auction;
        }

        @Override
        public void run() {
            try {
                runImpl();
            } catch (Exception e) {
                log.error("Failed scheduling auction {}", auction.getAuctionId(), e);
            }
        }

        private void runImpl() throws Exception {
            final ItemAuctionState state = auction.getAuctionState();
            switch (state) {
                case CREATED: {
                    if (!auction.setAuctionState(state, ItemAuctionState.STARTED)) {
                        throw new IllegalStateException(
                                "Could not set auction state: " + ItemAuctionState.STARTED + ", expected: " + state);
                    }

                    log.info("Auction {} has started for instance {}", auction.getAuctionId(), auction.getInstanceId());
                    checkAndSetCurrentAndNextAuction();
                    break;
                }
                case STARTED: {
                    switch (auction.getAuctionEndingExtendState()) {
                        case EXTEND_BY_5_MIN: {
                            if (auction.getScheduledAuctionEndingExtendState() == ItemAuctionExtendState.INITIAL) {
                                auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_5_MIN);
                                setStateTask(ThreadPool.schedule(
                                        this, Math.max(auction.getEndingTime() - System.currentTimeMillis(), 0)));
                                return;
                            }
                            break;
                        }
                        case EXTEND_BY_3_MIN: {
                            if (auction.getScheduledAuctionEndingExtendState()
                                    != ItemAuctionExtendState.EXTEND_BY_3_MIN) {
                                auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_3_MIN);
                                setStateTask(ThreadPool.schedule(
                                        this, Math.max(auction.getEndingTime() - System.currentTimeMillis(), 0)));
                                return;
                            }
                            break;
                        }
                        case EXTEND_BY_CONFIG_PHASE_A: {
                            if (auction.getScheduledAuctionEndingExtendState()
                                    != ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_B) {
                                auction.setScheduledAuctionEndingExtendState(
                                        ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_B);
                                setStateTask(ThreadPool.schedule(
                                        this, Math.max(auction.getEndingTime() - System.currentTimeMillis(), 0)));
                                return;
                            }
                            break;
                        }
                        case EXTEND_BY_CONFIG_PHASE_B: {
                            if (auction.getScheduledAuctionEndingExtendState()
                                    != ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A) {
                                auction.setScheduledAuctionEndingExtendState(
                                        ItemAuctionExtendState.EXTEND_BY_CONFIG_PHASE_A);
                                setStateTask(ThreadPool.schedule(
                                        this, Math.max(auction.getEndingTime() - System.currentTimeMillis(), 0)));
                                return;
                            }
                        }
                    }

                    if (!auction.setAuctionState(state, ItemAuctionState.FINISHED)) {
                        throw new IllegalStateException(
                                "Could not set auction state: " + ItemAuctionState.FINISHED + ", expected: " + state);
                    }

                    onAuctionFinished(auction);
                    checkAndSetCurrentAndNextAuction();
                    break;
                }
                default: {
                    throw new IllegalStateException("Invalid state: " + state);
                }
            }
        }
    }

    protected void onAuctionFinished(ItemAuction auction) {
        auction.broadcastToAllBiddersInternal(
                new SystemMessage(SystemMessageId.S1_S_AUCTION_HAS_ENDED).addInt(auction.getAuctionId()));

        final ItemAuctionBid bid = auction.getHighestBid();
        if (bid != null) {
            final Item item = auction.createNewItemInstance();
            final Player player = bid.getPlayer();
            if (player != null) {
                player.getWarehouse().addItem(ItemProcessType.BUY, item, null, null);
                player.sendPacket(
                        SystemMessageId
                                .YOU_HAVE_BID_THE_HIGHEST_PRICE_AND_HAVE_WON_THE_ITEM_THE_ITEM_CAN_BE_FOUND_IN_YOUR_PERSONAL_WAREHOUSE);

                log.info(
                        "Auction {} has finished. Highest bid by {} for instance {}",
                        auction.getAuctionId(),
                        player.getName(),
                        instanceId);
            } else {
                item.setOwnerId(bid.getPlayerObjId());
                item.setItemLocation(ItemLocation.WAREHOUSE);
                item.updateDatabase();
                World.removeObject(item);

                log.info(
                        "Auction {} has finished. Highest bid by {} for instance {}",
                        auction.getAuctionId(),
                        CharInfoTable.getInstance().getNameById(bid.getPlayerObjId()),
                        instanceId);
            }

            // Clean all canceled bids.
            auction.clearCanceledBids();
        } else {
            log.info(
                    "Auction {} has finished. There have not been any bid for instance {}",
                    auction.getAuctionId(),
                    instanceId);
        }
    }

    protected void setStateTask(ScheduledFuture<?> future) {
        final ScheduledFuture<?> stateTask = this.stateTask;
        if (stateTask != null) {
            stateTask.cancel(false);
        }

        this.stateTask = future;
    }

    private ItemAuction createAuction(long after) {
        final AuctionItem auctionItem = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        final long startingTime = dateGenerator.nextDate(after);
        final long endingTime =
                startingTime + TimeUnit.MILLISECONDS.convert(auctionItem.getAuctionLength(), TimeUnit.MINUTES);
        final ItemAuction auction =
                new ItemAuction(auctionIds.getAndIncrement(), instanceId, startingTime, endingTime, auctionItem);
        auction.storeMe();
        return auction;
    }

    private ItemAuction loadAuction(int auctionId) throws SQLException {
        try (Connection con = DatabaseFactory.getConnection()) {
            int auctionItemId = 0;
            long startingTime = 0;
            long endingTime = 0;
            byte auctionStateId = 0;
            try (PreparedStatement ps = con.prepareStatement(SELECT_AUCTION_INFO)) {
                ps.setInt(1, auctionId);
                try (ResultSet rset = ps.executeQuery()) {
                    if (!rset.next()) {
                        log.warn("Auction data not found for auction: {}", auctionId);
                        return null;
                    }

                    auctionItemId = rset.getInt(1);
                    startingTime = rset.getLong(2);
                    endingTime = rset.getLong(3);
                    auctionStateId = rset.getByte(4);
                }
            }

            if (startingTime >= endingTime) {
                log.warn("Invalid starting/ending paramaters for auction: {}", auctionId);
                return null;
            }

            final AuctionItem auctionItem = getAuctionItem(auctionItemId);
            if (auctionItem == null) {
                log.warn("AuctionItem: {}, not found for auction: {}", auctionItemId, auctionId);
                return null;
            }

            final ItemAuctionState auctionState = ItemAuctionState.stateForStateId(auctionStateId);
            if (auctionState == null) {
                log.warn("Invalid auctionStateId: {}, for auction: {}", auctionStateId, auctionId);
                return null;
            }

            if ((auctionState == ItemAuctionState.FINISHED)
                    && (startingTime
                            < (System.currentTimeMillis()
                                    - TimeUnit.MILLISECONDS.convert(
                                            GeneralConfig.ALT_ITEM_AUCTION_EXPIRED_AFTER, TimeUnit.DAYS)))) {
                log.info("Clearing expired auction: {}", auctionId);
                try (PreparedStatement ps = con.prepareStatement(DELETE_AUCTION_INFO_BY_AUCTION_ID)) {
                    ps.setInt(1, auctionId);
                    ps.execute();
                }

                try (PreparedStatement ps = con.prepareStatement(DELETE_AUCTION_BID_INFO_BY_AUCTION_ID)) {
                    ps.setInt(1, auctionId);
                    ps.execute();
                }

                return null;
            }

            final List<ItemAuctionBid> auctionBids = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(SELECT_PLAYERS_ID_BY_AUCTION_ID)) {
                ps.setInt(1, auctionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auctionBids.add(new ItemAuctionBid(rs.getInt(1), rs.getLong(2)));
                    }
                }
            }

            return new ItemAuction(
                    auctionId, instanceId, startingTime, endingTime, auctionItem, auctionBids, auctionState);
        }
    }
}
