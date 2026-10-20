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
package app.l2nx.highfive.managers;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.holders.ClanHallTemplate;
import app.l2nx.highfive.data.xml.ClanHallData;
import app.l2nx.highfive.entity.residences.ClanHallAuction;
import app.l2nx.highfive.repository.auction.ClanHallAuctionRepository;
import app.l2nx.highfive.repository.auction.model.ClanHallAuctionRow;
import app.l2nx.highfive.repository.auction.model.ClanHallBid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClanHallAuctionManager {
    private static volatile ClanHallAuctionManager instance;

    @Getter
    private final List<ClanHallAuction> auctions = new ArrayList<>();

    private final ClanHallAuctionRepository auctionRepository;

    public ClanHallAuctionManager(ClanHallAuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
        instance = this;
    }

    public void reload() {
        auctions.clear();
        load();
    }

    public void load() {
        try {
            for (int id : auctionRepository.findAllIds()) {
                auctions.add(new ClanHallAuction(id));
            }

            log.info("Loaded {} auction(s)", auctions.size());
        } catch (DataAccessException e) {
            log.warn("Exception: AuctionManager.load(): {}", e.getMessage(), e);
        }
    }

    public ClanHallAuction getAuction(int auctionId) {
        final int index = getAuctionIndex(auctionId);
        if (index >= 0) {
            return auctions.get(index);
        }

        return null;
    }

    public int getAuctionIndex(int auctionId) {
        ClanHallAuction auction;
        for (int i = 0; i < auctions.size(); i++) {
            auction = auctions.get(i);
            if ((auction != null) && (auction.getId() == auctionId)) {
                return i;
            }
        }

        return -1;
    }

    public void initNPC(int id) {
        final ClanHallTemplate hall = ClanHallData.getInstance().getClanHall(id);
        if ((hall == null) || (hall.auctionStartingBid() == null)) {
            log.warn("Clan Hall auction not found for Id :{}", id);
            return;
        }

        try {
            auctionRepository.create(ClanHallAuctionRow.builder()
                    .clanHallId(id)
                    .startingBid(hall.auctionStartingBid())
                    .endDate(instantOrNull(ClockBridge.wallMillis() + ClanHallAuction.AUCTION_LENGTH_MS))
                    .build());
            auctions.add(new ClanHallAuction(id));
            log.info("Created auction for ClanHall: {}", id);
        } catch (DataAccessException e) {
            log.error("Exception: Auction.initNPC(): {}", e.getMessage(), e);
        }
    }

    public Optional<ClanHallAuctionRow> findAuction(int clanHallId) {
        try {
            return auctionRepository.find(clanHallId);
        } catch (DataAccessException e) {
            log.warn("Exception: Auction.load(): {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * @return {@code false} when the auction could not be stored
     */
    public boolean saveAuction(ClanHallAuctionRow auction) {
        try {
            auctionRepository.create(auction);
            return true;
        } catch (DataAccessException e) {
            log.error("Exception: Auction.confirmAuction(): {}", e.getMessage(), e);
            return false;
        }
    }

    public void saveAuctionEndDate(int clanHallId, long endDate) {
        try {
            auctionRepository.updateEndDate(clanHallId, instantOrNull(endDate));
        } catch (DataAccessException e) {
            log.error("Exception: saveAuctionDate(): {}", e.getMessage(), e);
        }
    }

    public void deleteAuction(int clanHallId) {
        try {
            auctionRepository.delete(clanHallId);
        } catch (DataAccessException e) {
            log.error("Exception: Auction.deleteFromDB(): {}", e.getMessage(), e);
        }
    }

    /**
     * @return the bids, the highest first
     */
    public List<ClanHallBid> findBids(int clanHallId) {
        try {
            return auctionRepository.findBids(clanHallId);
        } catch (DataAccessException e) {
            log.warn("Exception: Auction.loadBid(): {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * @return {@code false} when the bid could not be stored
     */
    public boolean saveBid(int clanHallId, ClanHallBid bid) {
        try {
            auctionRepository.saveBid(clanHallId, bid);
            return true;
        } catch (DataAccessException e) {
            log.error("Exception: Auction.updateInDB(Player bidder, int bid): {}", e.getMessage(), e);
            return false;
        }
    }

    public void deleteBid(int clanHallId, int clanId) {
        try {
            auctionRepository.deleteBid(clanHallId, clanId);
        } catch (DataAccessException e) {
            log.error("Exception: Auction.cancelBid(String bidder): {}", e.getMessage(), e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static ClanHallAuctionManager getInstance() {
        final ClanHallAuctionManager current = instance;
        if (current == null) {
            throw new IllegalStateException("ClanHallAuctionManager is used before the Spring context created it.");
        }
        return current;
    }
}
