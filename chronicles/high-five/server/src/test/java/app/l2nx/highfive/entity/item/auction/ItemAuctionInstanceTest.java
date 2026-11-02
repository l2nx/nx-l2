/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item.auction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
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
