/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item.auction;

import static app.l2nx.highfive.entity.WorldTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.auction.ItemAuctionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class ItemAuctionTest {
    private static final int AUCTION = 7;
    private static final int INSTANCE = 32_320;
    private static final long INIT_BID = 100;
    private static final long START = ClockBridgeExtension.WALL_START.toEpochMilli();
    private static final long END = START + 86_400_000L;

    private final AuctionItem item = mock(AuctionItem.class);
    private final Player first = player(0x7ffe_3001);
    private final Player second = player(0x7ffe_3002);

    @BeforeEach
    void bindManager() {
        new ItemAuctionManager(mock(ItemAuctionRepository.class));
        when(item.createNewItemInstance()).thenReturn(mock(Item.class));
        when(item.getAuctionInitBid()).thenReturn(INIT_BID);
        for (Player player : List.of(first, second)) {
            when(player.reduceAdena(eq(ItemProcessType.FEE), anyLong(), eq(player), anyBoolean()))
                    .thenReturn(true);
        }
    }

    private ItemAuction startedAuction() {
        return new ItemAuction(AUCTION, INSTANCE, START, END, item, new ArrayList<>(), ItemAuctionState.STARTED);
    }

    private <T> T inWorld(Supplier<T> body) {
        return WorldLoopExtension.callInPass(() -> {
            World.addObject(first);
            World.addObject(second);
            try {
                return body.get();
            } finally {
                World.removeObject(first);
                World.removeObject(second);
            }
        });
    }

    @Nested
    class OwnershipCheck {
        private ItemAuction auction;

        @BeforeEach
        void createAuction() {
            auction = WorldLoopExtension.callInPass(ItemAuctionTest.this::startedAuction);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ItemAuction> call) {
            assertThatThrownBy(() -> call.accept(auction))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ItemAuction>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getAuctionId (accessor)", ItemAuction::getAuctionId),
                    Named.of("getInstanceId", ItemAuction::getInstanceId),
                    Named.of("getStartingTime", ItemAuction::getStartingTime),
                    Named.of("getEndingTime (former volatile)", ItemAuction::getEndingTime),
                    Named.of("getScheduledAuctionEndingExtendState", ItemAuction::getScheduledAuctionEndingExtendState),
                    Named.of(
                            "setScheduledAuctionEndingExtendState",
                            a -> a.setScheduledAuctionEndingExtendState(ItemAuctionExtendState.INITIAL)),
                    Named.of("getAuctionEndingExtendState", ItemAuction::getAuctionEndingExtendState),
                    Named.of("getItemInfo", ItemAuction::getItemInfo),
                    Named.of("getHighestBid", ItemAuction::getHighestBid),
                    Named.of("getAuctionState (former lock)", ItemAuction::getAuctionState),
                    Named.of(
                            "setAuctionState (former lock)",
                            a -> a.setAuctionState(ItemAuctionState.STARTED, ItemAuctionState.FINISHED)),
                    Named.of("createNewItemInstance", ItemAuction::createNewItemInstance),
                    Named.of("getAuctionInitBid", ItemAuction::getAuctionInitBid),
                    Named.of("getFinishingTimeRemaining", ItemAuction::getFinishingTimeRemaining),
                    Named.of("registerBid (former lock)", a -> a.registerBid(null, INIT_BID)),
                    Named.of("broadcastToAllBidders", a -> a.broadcastToAllBidders(null)),
                    Named.of("cancelBid (former lock)", a -> a.cancelBid(null)),
                    Named.of("clearCanceledBids (former lock)", ItemAuction::clearCanceledBids),
                    Named.of("getLastBid", a -> a.getLastBid(null)),
                    Named.of("getBidFor", a -> a.getBidFor(1)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bidCalls")
        void bidCall_shouldThrow_whenCalledOffTheLoop(Consumer<ItemAuctionBid> call) {
            final ItemAuctionBid bid = new ItemAuctionBid(1, INIT_BID);

            assertThatThrownBy(() -> call.accept(bid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ItemAuctionBid>>> bidCalls() {
            return Stream.of(
                    Named.of("getPlayerObjId (accessor)", ItemAuctionBid::getPlayerObjId),
                    Named.of("getLastBid", ItemAuctionBid::getLastBid),
                    Named.of("setLastBid", b -> b.setLastBid(INIT_BID)),
                    Named.of("cancelBid", ItemAuctionBid::cancelBid),
                    Named.of("isCanceled", ItemAuctionBid::isCanceled),
                    Named.of("getPlayer", ItemAuctionBid::getPlayer));
        }
    }

    @Nested
    class RegisterBid {
        @Test
        void registerBid_shouldMakeTheHigherBidTheHighest_whenItOutbidsAnother() {
            final List<Object> actual = inWorld(() -> {
                final ItemAuction auction = startedAuction();
                auction.registerBid(first, 200);
                auction.registerBid(second, 300);
                return List.of(
                        auction.getHighestBid().getPlayerObjId(),
                        auction.getLastBid(first),
                        auction.getLastBid(second));
            });

            assertThat(actual).containsExactly(second.getObjectId(), 200L, 300L);
        }

        @Test
        void registerBid_shouldTellTheOutbidBidder_whenAHigherBidArrives() {
            inWorld(() -> {
                final ItemAuction auction = startedAuction();
                auction.registerBid(first, 200);
                auction.registerBid(second, 300);
                return null;
            });

            verify(first).sendPacket(SystemMessageId.YOU_HAVE_BEEN_OUTBID);
            verify(second, never()).sendPacket(SystemMessageId.YOU_HAVE_BEEN_OUTBID);
        }

        @Test
        void registerBid_shouldRejectALowerBid_whenAHigherOneLeads() {
            final List<Object> actual = inWorld(() -> {
                final ItemAuction auction = startedAuction();
                auction.registerBid(first, 300);
                auction.registerBid(second, 200);
                return List.of(
                        auction.getHighestBid().getPlayerObjId(), auction.getBidFor(second.getObjectId()) == null);
            });

            assertThat(actual).containsExactly(first.getObjectId(), true);
            verify(second).sendPacket(SystemMessageId.YOUR_BID_MUST_BE_HIGHER_THAN_THE_CURRENT_HIGHEST_BID);
        }

        @Test
        void registerBid_shouldIgnoreTheBid_whenTheAuctionHasNotStarted() {
            final ItemAuctionBid actual = inWorld(() -> {
                final ItemAuction auction = new ItemAuction(AUCTION, INSTANCE, START, END, item);
                auction.registerBid(first, 200);
                return auction.getBidFor(first.getObjectId());
            });

            assertThat(actual).isNull();
            verify(first, never()).reduceAdena(eq(ItemProcessType.FEE), anyLong(), eq(first), anyBoolean());
        }
    }

    @Nested
    class CancelBid {
        @Test
        void cancelBid_shouldRefundTheBid_whenItIsNotTheHighest() {
            final List<Object> actual = inWorld(() -> {
                final ItemAuction auction = startedAuction();
                auction.registerBid(first, 200);
                auction.registerBid(second, 300);
                return List.of(
                        auction.cancelBid(first),
                        auction.getBidFor(first.getObjectId()).isCanceled());
            });

            assertThat(actual).containsExactly(true, true);
            verify(first).addAdena(ItemProcessType.REFUND, 200, first, true);
        }

        @Test
        void cancelBid_shouldKeepTheWinningBid() {
            final List<Object> actual = inWorld(() -> {
                final ItemAuction auction = startedAuction();
                auction.registerBid(first, 200);
                return List.of(auction.cancelBid(first), auction.getLastBid(first));
            });

            assertThat(actual).containsExactly(true, 200L);
            verify(first, never()).addAdena(eq(ItemProcessType.REFUND), anyLong(), eq(first), anyBoolean());
        }
    }

    @Nested
    class BroadcastToAllBidders {
        @Test
        void broadcastToAllBidders_shouldReachEveryBidderInTheWorld() {
            final ServerPacket packet = mock(ServerPacket.class);

            inWorld(() -> {
                final ItemAuction auction = startedAuction();
                auction.registerBid(first, 200);
                auction.registerBid(second, 300);
                auction.broadcastToAllBidders(packet);
                return null;
            });

            verify(first).sendPacket(packet);
            verify(second).sendPacket(packet);
        }
    }

    @Nested
    class SetAuctionState {
        @Test
        void setAuctionState_shouldChangeTheState_whenTheCurrentStateIsTheExpectedOne() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final ItemAuction auction = startedAuction();
                return List.of(
                        auction.setAuctionState(ItemAuctionState.STARTED, ItemAuctionState.FINISHED),
                        auction.getAuctionState());
            });

            assertThat(actual).containsExactly(true, ItemAuctionState.FINISHED);
        }

        @Test
        void setAuctionState_shouldKeepTheState_whenTheCurrentStateIsAnotherOne() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final ItemAuction auction = startedAuction();
                return List.of(
                        auction.setAuctionState(ItemAuctionState.CREATED, ItemAuctionState.FINISHED),
                        auction.getAuctionState());
            });

            assertThat(actual).containsExactly(false, ItemAuctionState.STARTED);
        }
    }
}
