/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.auction;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.entity.item.auction.ItemAuctionState;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.auction.model.ItemAuctionBidRow;
import app.l2nx.highfive.repository.auction.model.ItemAuctionRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Item auctions of the auction manager NPCs and their bids; the bids go with the auction.
 */
@Repository
@RequiredArgsConstructor
public class ItemAuctionRepository {
    private static final long CANCELLED_BID = -1;

    private static final String SELECT_IDS_BY_NPC = "SELECT id FROM item_auctions WHERE npc_id = ? ORDER BY id";
    private static final String SELECT_AUCTION = """
        SELECT id, npc_id, auction_item_id, starts_at, ends_at, state
        FROM item_auctions
        WHERE id = ?\
        """;
    private static final String INSERT_AUCTION = """
        INSERT INTO item_auctions (npc_id, auction_item_id, starts_at, ends_at, state)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id\
        """;
    private static final String UPDATE_STATE = "UPDATE item_auctions SET state = ? WHERE id = ?";
    private static final String DELETE_AUCTION = "DELETE FROM item_auctions WHERE id = ?";
    private static final String SELECT_BIDS =
            "SELECT character_id, bid_amount FROM item_auction_bids WHERE item_auction_id = ?";
    private static final String UPSERT_BID = """
        INSERT INTO item_auction_bids (item_auction_id, character_id, bid_amount)
        VALUES (?, ?, ?)
        ON CONFLICT (item_auction_id, character_id) DO UPDATE SET bid_amount = EXCLUDED.bid_amount\
        """;
    private static final String DELETE_BID =
            "DELETE FROM item_auction_bids WHERE item_auction_id = ? AND character_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<Integer> findIdsByInstance(int instanceId) {
        return jdbcClient
                .sql(SELECT_IDS_BY_NPC)
                .param(instanceId)
                .query(Integer.class)
                .list();
    }

    public Optional<ItemAuctionRow> find(int auctionId) {
        return jdbcClient
                .sql(SELECT_AUCTION)
                .param(auctionId)
                .query((rs, rowNum) -> ItemAuctionRow.builder()
                        .auctionId(rs.getInt("id"))
                        .instanceId(rs.getInt("npc_id"))
                        .auctionItemId(rs.getInt("auction_item_id"))
                        .startingTime(JdbcTimes.instant(rs, "starts_at"))
                        .endingTime(JdbcTimes.instant(rs, "ends_at"))
                        .state(ItemAuctionState.valueOf(rs.getString("state")))
                        .build())
                .optional();
    }

    /**
     * @param auction the auction to store; its {@code auctionId} is not read
     * @return the id the database assigned: shown to the client, never reused
     */
    public int create(ItemAuctionRow auction) {
        return jdbcClient
                .sql(INSERT_AUCTION)
                .param(auction.instanceId())
                .param(auction.auctionItemId())
                .param(JdbcTimes.utc(auction.startingTime()))
                .param(JdbcTimes.utc(auction.endingTime()))
                .param(auction.state().name())
                .query(Integer.class)
                .single();
    }

    public void updateState(int auctionId, ItemAuctionState state) {
        jdbcClient.sql(UPDATE_STATE).param(state.name()).param(auctionId).update();
    }

    public void delete(int auctionId) {
        jdbcClient.sql(DELETE_AUCTION).param(auctionId).update();
    }

    public List<ItemAuctionBidRow> findBids(int auctionId) {
        return jdbcClient
                .sql(SELECT_BIDS)
                .param(auctionId)
                .query((rs, rowNum) -> {
                    final Long bid = rs.getObject("bid_amount", Long.class);
                    return ItemAuctionBidRow.builder()
                            .characterId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                            .lastBid(bid == null ? CANCELLED_BID : bid)
                            .build();
                })
                .list();
    }

    public void saveBid(int auctionId, ItemAuctionBidRow bid) {
        jdbcClient
                .sql(UPSERT_BID)
                .param(auctionId)
                .param(References.required(registry, bid.characterId()))
                .param(bid.lastBid() == CANCELLED_BID ? null : bid.lastBid())
                .update();
    }

    public void deleteBid(int auctionId, int characterId) {
        registry.findId(characterId)
                .ifPresent(id ->
                        jdbcClient.sql(DELETE_BID).param(auctionId).param(id).update());
    }
}
