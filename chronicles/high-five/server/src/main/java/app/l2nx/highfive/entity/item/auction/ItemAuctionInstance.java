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
package app.l2nx.highfive.entity.item.auction;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.auction.model.ItemAuctionBidRow;
import app.l2nx.highfive.repository.auction.model.ItemAuctionRow;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Slf4j
public class ItemAuctionInstance {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yy");

    private static final long START_TIME_SPACE = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
    private static final long FINISH_TIME_SPACE = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

    // SQL queries

    private final int instanceId;
    private final Map<Integer, ItemAuction> auctions;
    private final List<AuctionItem> items;
    private final AuctionDateGenerator dateGenerator;

    @Getter
    private ItemAuction currentAuction;

    @Getter
    private ItemAuction nextAuction;

    private ScheduledFuture<?> stateTask;

    public ItemAuctionInstance(int instanceId, Node node) throws Exception {
        this.instanceId = instanceId;
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

        final List<Integer> auctionIds;
        try {
            auctionIds = ItemAuctionManager.getInstance().findAuctionIds(this.instanceId);
        } catch (DataAccessException e) {
            log.error("Failed loading auctions.", e);
            return;
        }

        for (int auctionId : auctionIds) {
            try {
                final ItemAuction auction = loadAuction(auctionId);
                if (auction != null) {
                    auctions.put(auctionId, auction);
                } else {
                    ItemAuctionManager.getInstance().deleteAuction(auctionId);
                }
            } catch (DataAccessException e) {
                log.warn("Failed loading auction: {}", auctionId, e);
            }
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
                nextAuction = createAuction(ClockBridge.wallMillis() + START_TIME_SPACE);
                break;
            }
            case 1: {
                switch (auctions[0].getAuctionState()) {
                    case CREATED: {
                        if (auctions[0].getStartingTime() < (ClockBridge.wallMillis() + START_TIME_SPACE)) {
                            currentAuction = auctions[0];
                            nextAuction = createAuction(ClockBridge.wallMillis() + START_TIME_SPACE);
                        } else {
                            nextAuction = auctions[0];
                        }
                        break;
                    }
                    case STARTED: {
                        currentAuction = auctions[0];
                        nextAuction = createAuction(Math.max(
                                currentAuction.getEndingTime() + FINISH_TIME_SPACE,
                                ClockBridge.wallMillis() + START_TIME_SPACE));
                        break;
                    }
                    case FINISHED: {
                        currentAuction = auctions[0];
                        nextAuction = createAuction(ClockBridge.wallMillis() + START_TIME_SPACE);
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
                final long currentTime = ClockBridge.wallMillis();

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
                    nextAuction = createAuction(ClockBridge.wallMillis() + START_TIME_SPACE);
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
                        Math.max(currentAuction.getEndingTime() - ClockBridge.wallMillis(), 0)));
            } else {
                setStateTask(ThreadPool.schedule(
                        new ScheduleAuctionTask(currentAuction),
                        Math.max(currentAuction.getStartingTime() - ClockBridge.wallMillis(), 0)));
            }

            log.info("Schedule current auction {} for instance {}", currentAuction.getAuctionId(), instanceId);
        } else {
            setStateTask(ThreadPool.schedule(
                    new ScheduleAuctionTask(nextAuction),
                    Math.max(nextAuction.getStartingTime() - ClockBridge.wallMillis(), 0)));
            log.info(
                    "Schedule next auction {} on {} for instance {}",
                    nextAuction.getAuctionId(),
                    DATE_FORMAT.format(Instant.ofEpochMilli(nextAuction.getStartingTime())
                            .atZone(ClockBridge.wallClock().getZone())),
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
                                        this, Math.max(auction.getEndingTime() - ClockBridge.wallMillis(), 0)));
                                return;
                            }
                            break;
                        }
                        case EXTEND_BY_3_MIN: {
                            if (auction.getScheduledAuctionEndingExtendState()
                                    != ItemAuctionExtendState.EXTEND_BY_3_MIN) {
                                auction.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.EXTEND_BY_3_MIN);
                                setStateTask(ThreadPool.schedule(
                                        this, Math.max(auction.getEndingTime() - ClockBridge.wallMillis(), 0)));
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
                                        this, Math.max(auction.getEndingTime() - ClockBridge.wallMillis(), 0)));
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
                                        this, Math.max(auction.getEndingTime() - ClockBridge.wallMillis(), 0)));
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
        final AuctionItem auctionItem = items.get(ClockBridge.random().nextInt(items.size()));
        final long startingTime = dateGenerator.nextDate(after);
        final long endingTime =
                startingTime + TimeUnit.MILLISECONDS.convert(auctionItem.getAuctionLength(), TimeUnit.MINUTES);
        final int auctionId = ItemAuctionManager.getInstance()
                .createAuction(ItemAuctionRow.builder()
                        .instanceId(instanceId)
                        .auctionItemId(auctionItem.getAuctionItemId())
                        .startingTime(instantOrNull(startingTime))
                        .endingTime(instantOrNull(endingTime))
                        .state(ItemAuctionState.CREATED)
                        .build());
        return new ItemAuction(auctionId, instanceId, startingTime, endingTime, auctionItem);
    }

    private ItemAuction loadAuction(int auctionId) {
        final ItemAuctionRow row =
                ItemAuctionManager.getInstance().findAuction(auctionId).orElse(null);
        if (row == null) {
            log.warn("Auction data not found for auction: {}", auctionId);
            return null;
        }

        final long startingTime = epochMillisOrZero(row.startingTime());
        final long endingTime = epochMillisOrZero(row.endingTime());
        if (startingTime >= endingTime) {
            log.warn("Invalid starting/ending paramaters for auction: {}", auctionId);
            return null;
        }

        final AuctionItem auctionItem = getAuctionItem(row.auctionItemId());
        if (auctionItem == null) {
            log.warn("AuctionItem: {}, not found for auction: {}", row.auctionItemId(), auctionId);
            return null;
        }

        final ItemAuctionState auctionState = row.state();
        if ((auctionState == ItemAuctionState.FINISHED)
                && (startingTime
                        < (ClockBridge.wallMillis()
                                - TimeUnit.MILLISECONDS.convert(
                                        GeneralConfig.ALT_ITEM_AUCTION_EXPIRED_AFTER, TimeUnit.DAYS)))) {
            log.info("Clearing expired auction: {}", auctionId);
            return null;
        }

        final List<ItemAuctionBid> auctionBids = new ArrayList<>();
        for (ItemAuctionBidRow bid : ItemAuctionManager.getInstance().findBids(auctionId)) {
            auctionBids.add(new ItemAuctionBid(bid.characterId(), bid.lastBid()));
        }

        return new ItemAuction(auctionId, instanceId, startingTime, endingTime, auctionItem, auctionBids, auctionState);
    }
}
