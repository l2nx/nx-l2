/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences;

import static app.l2nx.highfive.entity.itemcontainer.Inventory.ADENA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.ClanHallData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.repository.auction.ClanHallAuctionRepository;
import app.l2nx.highfive.repository.auction.model.ClanHallAuctionRow;
import app.l2nx.highfive.repository.auction.model.ClanHallBid;
import app.l2nx.highfive.threads.ThreadPool;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class ClanHallAuctionTest {
    private static final int HALL = 22;
    private static final int BIDDER_CLAN = 7;
    private static final long STARTING_BID = 100;
    private static final long BID = 500;

    private final ClanHallAuctionRepository repository = mock(ClanHallAuctionRepository.class);
    private final Player bidder = mock(Player.class);
    private final Clan bidderClan = mock(Clan.class);
    private final ItemContainer warehouse = mock(ItemContainer.class);

    private long maxAdena;
    private ClanHallAuction auction;

    @BeforeEach
    void createAuction() throws IllegalAccessException {
        // Class init before the config is set: the order that froze the adena cap at 0 in production.
        MethodHandles.lookup().ensureInitialized(Inventory.class);
        maxAdena = PlayerConfig.MAX_ADENA;
        PlayerConfig.MAX_ADENA = 99_900_000_000L;
        new ClanHallAuctionManager(repository);
        when(bidder.getClan()).thenReturn(bidderClan);
        when(bidder.getClanId()).thenReturn(BIDDER_CLAN);
        when(bidderClan.getWarehouse()).thenReturn(warehouse);
        when(warehouse.getAdena()).thenReturn(1_000L);
        auction = new ClanHallAuction(HALL, mock(Clan.class), ClanHallAuction.AUCTION_LENGTH_MS, STARTING_BID, "Hall");
    }

    @AfterEach
    void restoreConfig() {
        PlayerConfig.MAX_ADENA = maxAdena;
    }

    @Nested
    class SetBid {
        private MockedStatic<ClanTable> clanTable;

        @BeforeEach
        void bindClanTable() {
            clanTable = mockStatic(ClanTable.class);
            clanTable.when(ClanTable::getInstance).thenReturn(mock(ClanTable.class));
        }

        @AfterEach
        void releaseClanTable() {
            clanTable.close();
        }

        @Test
        void setBid_shouldTakeAdenaAndFlagClan_whenBidIsStored() {
            auction.setBid(bidder, BID);

            verify(warehouse).destroyItemByItemId(ItemProcessType.BUY, ADENA_ID, BID, bidder, bidder);
            verify(warehouse, never()).addItem(any(), anyInt(), anyLong(), any(), any());
            verify(bidderClan).setAuctionBiddedAt(HALL, true);
            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_CLAN);
        }

        @Test
        void setBid_shouldRefundAdenaAndKeepClanFree_whenBidIsNotStored() {
            doThrow(new DataAccessResourceFailureException("database down"))
                    .when(repository)
                    .saveBid(eq(HALL), any());

            auction.setBid(bidder, BID);

            verify(warehouse).addItem(ItemProcessType.REFUND, ADENA_ID, BID, null, null);
            verify(bidderClan, never()).setAuctionBiddedAt(anyInt(), anyBoolean());
            assertThat(auction.getHighestBidderId()).isZero();
            assertThat(auction.getBidders()).isEmpty();
        }
    }

    @Nested
    class CancelAuction {
        private static final int SELLER = 268_435_456;
        private static final int BIDDER = 268_435_457;

        private final ClanHallAuctionManager manager = mock(ClanHallAuctionManager.class);
        private final ClanHallTable halls = mock(ClanHallTable.class);
        private final ScheduledFuture<?> endTask = mock(ScheduledFuture.class);
        private final List<ClanHallAuction> auctions = new ArrayList<>();
        private final List<MockedStatic<?>> bridges = new ArrayList<>();

        @BeforeEach
        void bindBridges() {
            final ClanTable clans = mock(ClanTable.class);
            bind(ClanHallAuctionManager.class, ClanHallAuctionManager::getInstance, manager);
            bind(ClanHallTable.class, ClanHallTable::getInstance, halls);
            bind(ClanTable.class, ClanTable::getInstance, clans);
            bind(ClanHallData.class, ClanHallData::getInstance, mock(ClanHallData.class));
            final MockedStatic<ThreadPool> threadPool = mockStatic(ThreadPool.class);
            threadPool.when(() -> ThreadPool.schedule(any(), anyLong())).thenAnswer(invocation -> endTask);
            bridges.add(threadPool);

            when(manager.getAuctions()).thenReturn(auctions);
            when(manager.findAuction(HALL))
                    .thenReturn(Optional.of(ClanHallAuctionRow.builder()
                            .clanHallId(HALL)
                            .sellerClanId(SELLER)
                            .startingBid(STARTING_BID)
                            .endDate(ClockBridgeExtension.WALL_START.plusSeconds(86_400))
                            .build()));
            when(halls.loaded()).thenReturn(true);
            when(halls.getAuctionableHallById(HALL)).thenReturn(mock(AuctionableHall.class));
            when(clans.getClan(anyInt())).thenReturn(mock(Clan.class));
        }

        @AfterEach
        void releaseBridges() {
            bridges.forEach(MockedStatic::close);
        }

        private <T> void bind(Class<T> type, MockedStatic.Verification getInstance, T instance) {
            final MockedStatic<T> bridge = mockStatic(type);
            bridge.when(getInstance).thenReturn(instance);
            bridges.add(bridge);
        }

        private ClanHallAuction storedSale() {
            final ClanHallAuction sale = new ClanHallAuction(HALL);
            auctions.add(sale);
            return sale;
        }

        @Test
        void cancelAuction_shouldCancelScheduledEnd() {
            final ClanHallAuction sale = storedSale();

            sale.cancelAuction();

            verify(endTask).cancel(false);
        }

        @Test
        void endAuction_shouldKeepNextAuction_whenCancelledSaleHadNoBids() {
            final ClanHallAuction sale = storedSale();
            final ClanHallAuction next = mock(ClanHallAuction.class);
            when(next.getId()).thenReturn(HALL);
            sale.cancelAuction();
            auctions.add(next);

            sale.endAuction();

            assertThat(auctions).containsExactly(next);
        }

        @Test
        void endAuction_shouldNotSellHall_whenCancelledSaleHadBids() {
            when(manager.findBids(HALL))
                    .thenReturn(List.of(ClanHallBid.builder()
                            .clanId(BIDDER)
                            .bid(BID)
                            .timeBid(ClockBridgeExtension.WALL_START)
                            .build()));
            final ClanHallAuction sale = storedSale();
            sale.cancelAuction();

            sale.endAuction();

            verify(halls, never()).setOwner(anyInt(), any());
            verify(manager).deleteAuction(HALL);
        }
    }
}
