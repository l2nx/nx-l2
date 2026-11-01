/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.itemcontainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PlayerRefundTest {
    private static final int SWORD_ID = 20_000_030;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem single = new EtcItem(new StatSet(Map.of("item_id", SWORD_ID, "name", "Single")));

    @BeforeEach
    void managers() {
        new IdManager(registry);
        final TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
                    invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                    return null;
                })
                .when(transactions)
                .executeWithoutResult(any());
        new ItemManager(
                mock(ItemRepository.class),
                mock(ItemAugmentationRepository.class),
                mock(ItemElementalRepository.class),
                mock(ItemVariableRepository.class),
                transactions);
    }

    @Nested
    class DeleteMe {
        @Test
        void deleteMe_shouldEmptyTheRefund_whenTheDestroyOfAnItemFails() {
            final PlayerRefund refund = new PlayerRefund(null);
            final Item broken = mock(Item.class);
            when(broken.getObjectId()).thenReturn(registry.allocateTransient());
            doThrow(new IllegalStateException("destroy failed")).when(broken).setCount(anyLong());
            final int actual = WorldLoopExtension.callInPass(() -> {
                refund.addItem(broken);
                refund.addItem(new Item(registry.allocateTransient(), single));
                refund.addItem(new Item(registry.allocateTransient(), single));
                refund.deleteMe();
                return refund.getSize();
            });

            assertThat(actual).isZero();
        }

        @Test
        void deleteMe_shouldRemoveEveryItemFromTheWorld_whenTheDestroyOfAnItemFails() {
            final PlayerRefund refund = new PlayerRefund(null);
            final Item broken = mock(Item.class);
            when(broken.getObjectId()).thenReturn(registry.allocateTransient());
            doThrow(new IllegalStateException("destroy failed")).when(broken).setCount(anyLong());
            final List<WorldObject> actual = WorldLoopExtension.callInPass(() -> {
                final List<Item> items = List.of(
                        broken,
                        new Item(registry.allocateTransient(), single),
                        new Item(registry.allocateTransient(), single));
                for (Item item : items) {
                    World.addObject(item);
                    refund.addItem(item);
                }
                refund.deleteMe();
                return items.stream()
                        .map(item -> World.findObject(item.getObjectId()))
                        .filter(Objects::nonNull)
                        .toList();
            });

            assertThat(actual).isEmpty();
        }
    }
}
