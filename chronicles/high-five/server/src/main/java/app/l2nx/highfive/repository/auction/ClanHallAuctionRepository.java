/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.auction;

import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.auction.model.ClanHallAuctionRow;
import app.l2nx.highfive.repository.auction.model.ClanHallBid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanHallAuctionRepository {
    private static final String SELECT_IDS = "SELECT clan_hall_id FROM clan_hall_auctions ORDER BY clan_hall_id";
    private static final String SELECT_AUCTION = """
        SELECT clan_hall_id, seller_clan_id, starting_bid, current_bid, ends_at
        FROM clan_hall_auctions
        WHERE clan_hall_id = ?\
        """;
    private static final String INSERT_AUCTION = """
        INSERT INTO clan_hall_auctions (clan_hall_id, seller_clan_id, starting_bid, current_bid, ends_at)
        VALUES (?, ?, ?, ?, ?)\
        """;
    private static final String UPDATE_ENDS_AT = "UPDATE clan_hall_auctions SET ends_at = ? WHERE clan_hall_id = ?";
    private static final String DELETE_AUCTION = "DELETE FROM clan_hall_auctions WHERE clan_hall_id = ?";
    private static final String SELECT_BIDS = """
        SELECT clan_id, bid_amount, bid_at
        FROM clan_hall_auction_bids
        WHERE clan_hall_id = ?
        ORDER BY bid_amount DESC\
        """;
    private static final String UPSERT_BID = """
        INSERT INTO clan_hall_auction_bids (clan_hall_id, clan_id, bid_amount, bid_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (clan_hall_id, clan_id) DO UPDATE SET bid_amount = EXCLUDED.bid_amount, bid_at = EXCLUDED.bid_at\
        """;
    private static final String DELETE_BID =
            "DELETE FROM clan_hall_auction_bids WHERE clan_hall_id = ? AND clan_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<Integer> findAllIds() {
        return jdbcClient.sql(SELECT_IDS).query(Integer.class).list();
    }

    public Optional<ClanHallAuctionRow> find(int clanHallId) {
        return jdbcClient
                .sql(SELECT_AUCTION)
                .param(clanHallId)
                .query((rs, rowNum) -> {
                    final UUID sellerClanId = rs.getObject("seller_clan_id", UUID.class);
                    return ClanHallAuctionRow.builder()
                            .clanHallId(rs.getInt("clan_hall_id"))
                            .sellerClanId(sellerClanId == null ? 0 : registry.handleOf(sellerClanId))
                            .startingBid(rs.getLong("starting_bid"))
                            .currentBid(rs.getLong("current_bid"))
                            .endDate(JdbcTimes.instant(rs, "ends_at"))
                            .build();
                })
                .optional();
    }

    public void create(ClanHallAuctionRow auction) {
        jdbcClient
                .sql(INSERT_AUCTION)
                .param(auction.clanHallId())
                .param(requiredOrNull(registry, auction.sellerClanId()))
                .param(auction.startingBid())
                .param(auction.currentBid() == 0 ? null : auction.currentBid())
                .param(JdbcTimes.utc(auction.endDate()))
                .update();
    }

    public void updateEndDate(int clanHallId, Instant endDate) {
        jdbcClient
                .sql(UPDATE_ENDS_AT)
                .param(JdbcTimes.utc(endDate))
                .param(clanHallId)
                .update();
    }

    /**
     * Deletes the auction with its bids.
     */
    public void delete(int clanHallId) {
        jdbcClient.sql(DELETE_AUCTION).param(clanHallId).update();
    }

    /**
     * @return the bids, the highest first
     */
    public List<ClanHallBid> findBids(int clanHallId) {
        return jdbcClient
                .sql(SELECT_BIDS)
                .param(clanHallId)
                .query((rs, rowNum) -> ClanHallBid.builder()
                        .clanId(registry.handleOf(rs.getObject("clan_id", UUID.class)))
                        .bid(rs.getLong("bid_amount"))
                        .timeBid(JdbcTimes.instant(rs, "bid_at"))
                        .build())
                .list();
    }

    /**
     * A clan bids on one auction at a time.
     */
    public void saveBid(int clanHallId, ClanHallBid bid) {
        jdbcClient
                .sql(UPSERT_BID)
                .param(clanHallId)
                .param(References.required(registry, bid.clanId()))
                .param(bid.bid())
                .param(JdbcTimes.utc(bid.timeBid()))
                .update();
    }

    public void deleteBid(int clanHallId, int clanId) {
        registry.findId(clanId)
                .ifPresent(id ->
                        jdbcClient.sql(DELETE_BID).param(clanHallId).param(id).update());
    }
}
