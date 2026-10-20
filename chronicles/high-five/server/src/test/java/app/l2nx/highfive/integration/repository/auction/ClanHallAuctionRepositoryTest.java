/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.auction;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.auction.ClanHallAuctionRepository;
import app.l2nx.highfive.repository.auction.model.ClanHallAuctionRow;
import app.l2nx.highfive.repository.auction.model.ClanHallBid;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanHallAuctionRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CLAN_HALL_IDS = new AtomicInteger(10_000);

    private final ClanHallAuctionRepository repository = new ClanHallAuctionRepository(JDBC, registry);

    @Nested
    class Create {
        @Test
        void create_shouldStoreNpcAuctionWithSentinelsAsNull() {
            final ClanHallAuctionRow auction = auction(0);

            repository.create(auction);

            assertThat(JDBC.sql("SELECT seller_clan_id, current_bid FROM clan_hall_auctions WHERE clan_hall_id = ?")
                            .param(auction.clanHallId())
                            .query()
                            .singleRow())
                    .containsEntry("seller_clan_id", null)
                    .containsEntry("current_bid", null);
            assertThat(repository.find(auction.clanHallId())).contains(auction);
        }

        @Test
        void create_shouldStoreSellerClan() {
            final ClanHallAuctionRow auction = auction(registry.handleOf(insertClan())).toBuilder()
                    .currentBid(25_000_000)
                    .build();

            repository.create(auction);

            assertThat(repository.find(auction.clanHallId())).contains(auction);
        }
    }

    @Nested
    class FindAllIds {
        @Test
        void findAllIds_shouldContainCreatedAuctions() {
            final ClanHallAuctionRow auction = auction(0);
            repository.create(auction);

            assertThat(repository.findAllIds()).contains(auction.clanHallId());
        }
    }

    @Nested
    class UpdateEndDate {
        @Test
        void updateEndDate_shouldStoreEndDate() {
            final ClanHallAuctionRow auction = auction(0);
            repository.create(auction);
            final Instant endDate = NOW.plusSeconds(86_400);

            repository.updateEndDate(auction.clanHallId(), endDate);

            assertThat(repository.find(auction.clanHallId()))
                    .get()
                    .extracting(ClanHallAuctionRow::endDate)
                    .isEqualTo(endDate);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveAuctionWithBids() {
            final ClanHallAuctionRow auction = auction(0);
            repository.create(auction);
            repository.saveBid(auction.clanHallId(), bid(30_000_000));

            repository.delete(auction.clanHallId());

            assertThat(repository.find(auction.clanHallId())).isEmpty();
            assertThat(repository.findBids(auction.clanHallId())).isEmpty();
        }
    }

    @Nested
    class SaveBid {
        @Test
        void saveBid_shouldInsertThenRaiseAndListHighestFirst() {
            final ClanHallAuctionRow auction = auction(0);
            repository.create(auction);
            final ClanHallBid low = bid(21_000_000);
            final ClanHallBid high = bid(22_000_000);
            repository.saveBid(auction.clanHallId(), low);
            repository.saveBid(auction.clanHallId(), high);
            final ClanHallBid raised =
                    low.toBuilder().bid(23_000_000).timeBid(NOW.plusSeconds(60)).build();

            repository.saveBid(auction.clanHallId(), raised);

            assertThat(repository.findBids(auction.clanHallId())).containsExactly(raised, high);
        }
    }

    @Nested
    class DeleteBid {
        @Test
        void deleteBid_shouldRemoveOnlyTheClansBid() {
            final ClanHallAuctionRow auction = auction(0);
            repository.create(auction);
            final ClanHallBid removed = bid(21_000_000);
            final ClanHallBid kept = bid(22_000_000);
            repository.saveBid(auction.clanHallId(), removed);
            repository.saveBid(auction.clanHallId(), kept);

            repository.deleteBid(auction.clanHallId(), removed.clanId());

            assertThat(repository.findBids(auction.clanHallId())).containsExactly(kept);
        }
    }

    private static ClanHallAuctionRow auction(int sellerClanId) {
        return ClanHallAuctionRow.builder()
                .clanHallId(CLAN_HALL_IDS.incrementAndGet())
                .sellerClanId(sellerClanId)
                .startingBid(20_000_000)
                .currentBid(0)
                .endDate(NOW)
                .build();
    }

    private ClanHallBid bid(long amount) {
        return ClanHallBid.builder()
                .clanId(registry.handleOf(insertClan()))
                .bid(amount)
                .timeBid(NOW)
                .build();
    }
}
