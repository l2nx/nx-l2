/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item.holders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
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
class PremiumItemTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<PremiumItem> call) {
            final PremiumItem item = new PremiumItem(57, 10, "GM");

            assertThatThrownBy(() -> call.accept(item))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<PremiumItem>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getItemId (accessor)", PremiumItem::getItemId),
                    Named.of("getCount (accessor)", PremiumItem::getCount),
                    Named.of("getSender (accessor)", PremiumItem::getSender),
                    Named.of("updateCount", item -> item.updateCount(5)));
        }
    }

    @Nested
    class UpdateCount {
        @Test
        void updateCount_shouldReplaceTheCount() {
            final PremiumItem item = new PremiumItem(57, 10, "GM");

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                item.updateCount(3);
                return List.of(item.getItemId(), item.getCount(), item.getSender());
            });

            assertThat(actual).containsExactly(57, 3L, "GM");
        }
    }
}
