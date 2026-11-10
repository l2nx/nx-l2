/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item.auction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.PlayerWarehouse;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ItemAuctionInstanceTest {
    // The real constructor parses the auction XML and loads the database; the check comes before any field access.
    private final ItemAuctionInstance instance = mock(ItemAuctionInstance.class, CALLS_REAL_METHODS);

    @Nested
    class OnAuctionFinished {
        private final IdentityRegistry registry =
                new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
        private final UUID winnerId = EntityIds.next();
        private final ItemAuction auction = mock(ItemAuction.class);
        private final ItemAuctionBid bid = mock(ItemAuctionBid.class);
        private final Player winner = mock(Player.class);
        private final PlayerWarehouse warehouse = mock(PlayerWarehouse.class);
        private final Item won = mock(Item.class);

        @Test
        void onAuctionFinished_shouldCreateTheWonItem_onlyOnceTheWinnerSettled() {
            new IdManager(registry);
            when(auction.getHighestBid()).thenReturn(bid);
            when(auction.createNewItemInstance()).thenReturn(won);
            when(bid.getPlayerObjId()).thenReturn(registry.handleOf(winnerId));
            when(bid.getPlayer()).thenReturn(winner);
            when(winner.getWarehouse()).thenReturn(warehouse);
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.loadedCharacters().startLoading(winnerId);
                instance.onAuctionFinished(auction);
            });
            verify(auction, never()).createNewItemInstance();

            WorldLoopExtension.runInPass(() -> WorldPartition.loadedCharacters().loadEnded(winnerId));

            verify(auction).createNewItemInstance();
            verify(warehouse).addItem(ItemProcessType.BUY, won, null, null);
        }
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ItemAuctionInstance> call) {
            assertThatThrownBy(() -> call.accept(instance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ItemAuctionInstance>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getCurrentAuction (accessor)", ItemAuctionInstance::getCurrentAuction),
                    Named.of("getNextAuction (accessor)", ItemAuctionInstance::getNextAuction),
                    Named.of("shutdown", ItemAuctionInstance::shutdown),
                    Named.of("getAuction", i -> i.getAuction(1)),
                    Named.of("getAuctionsByBidder (former synchronized)", i -> i.getAuctionsByBidder(1)));
        }
    }
}
