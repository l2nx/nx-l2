/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.auction;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.item.auction.ItemAuctionState;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.auction.ItemAuctionRepository;
import app.l2nx.highfive.repository.auction.model.ItemAuctionBidRow;
import app.l2nx.highfive.repository.auction.model.ItemAuctionRow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemAuctionRepositoryTest extends RepositoryTest {
    private static final AtomicInteger NPC_IDS = new AtomicInteger(32_000);

    private final ItemAuctionRepository repository = new ItemAuctionRepository(JDBC, registry);

    @Nested
    class Create {
        @Test
        void create_shouldAssignIdAndStoreAuction() {
            final ItemAuctionRow auction = auction(NPC_IDS.incrementAndGet());

            final int id = repository.create(auction);

            assertThat(repository.find(id))
                    .contains(auction.toBuilder().auctionId(id).build());
        }

        @Test
        void create_shouldNeverReuseId_whenPreviousDeleted() {
            final int first = repository.create(auction(NPC_IDS.incrementAndGet()));
            repository.delete(first);

            assertThat(repository.create(auction(NPC_IDS.incrementAndGet()))).isGreaterThan(first);
        }
    }

    @Nested
    class FindIdsByInstance {
        @Test
        void findIdsByInstance_shouldReturnAuctionsOfTheNpc() {
            final int npc = NPC_IDS.incrementAndGet();
            final int first = repository.create(auction(npc));
            final int second = repository.create(auction(npc));
            repository.create(auction(NPC_IDS.incrementAndGet()));

            assertThat(repository.findIdsByInstance(npc)).containsExactly(first, second);
        }
    }

    @Nested
    class UpdateState {
        @Test
        void updateState_shouldStoreState() {
            final int id = repository.create(auction(NPC_IDS.incrementAndGet()));

            repository.updateState(id, ItemAuctionState.FINISHED);

            assertThat(repository.find(id))
                    .get()
                    .extracting(ItemAuctionRow::state)
                    .isEqualTo(ItemAuctionState.FINISHED);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveAuctionWithBids() {
            final int id = repository.create(auction(NPC_IDS.incrementAndGet()));
            repository.saveBid(id, bid(1000));

            repository.delete(id);

            assertThat(repository.find(id)).isEmpty();
            assertThat(repository.findBids(id)).isEmpty();
        }
    }

    @Nested
    class SaveBid {
        @Test
        void saveBid_shouldInsertThenRaiseBid() {
            final int id = repository.create(auction(NPC_IDS.incrementAndGet()));
            final ItemAuctionBidRow bid = bid(1000);
            repository.saveBid(id, bid);
            final ItemAuctionBidRow raised = bid.toBuilder().lastBid(2000).build();

            repository.saveBid(id, raised);

            assertThat(repository.findBids(id)).containsExactly(raised);
        }

        @Test
        void saveBid_shouldStoreCancelledBidAsNull() {
            final int id = repository.create(auction(NPC_IDS.incrementAndGet()));
            final ItemAuctionBidRow cancelled = bid(-1);

            repository.saveBid(id, cancelled);

            assertThat(JDBC.sql("SELECT bid_amount IS NULL FROM item_auction_bids WHERE item_auction_id = ?")
                            .param(id)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
            assertThat(repository.findBids(id)).containsExactly(cancelled);
        }
    }

    @Nested
    class DeleteBid {
        @Test
        void deleteBid_shouldRemoveOnlyThePlayersBid() {
            final int id = repository.create(auction(NPC_IDS.incrementAndGet()));
            final ItemAuctionBidRow removed = bid(1000);
            final ItemAuctionBidRow kept = bid(1500);
            repository.saveBid(id, removed);
            repository.saveBid(id, kept);

            repository.deleteBid(id, removed.characterId());

            assertThat(repository.findBids(id)).containsExactly(kept);
        }
    }

    private static ItemAuctionRow auction(int npc) {
        return ItemAuctionRow.builder()
                .instanceId(npc)
                .auctionItemId(7)
                .startingTime(NOW)
                .endingTime(NOW.plusSeconds(3600))
                .state(ItemAuctionState.CREATED)
                .build();
    }

    private ItemAuctionBidRow bid(long amount) {
        return ItemAuctionBidRow.builder()
                .characterId(registry.handleOf(insertCharacter()))
                .lastBid(amount)
                .build();
    }
}
