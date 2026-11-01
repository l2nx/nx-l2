/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.itemcontainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
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
class PlayerInventoryTest {
    private static final int QUEST_ITEM_ID = 20_000_040;
    private static final int RING_ID = 20_000_041;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem questItem =
            new EtcItem(new StatSet(Map.of("item_id", QUEST_ITEM_ID, "name", "Quest", "is_questitem", true)));
    private final EtcItem adena =
            new EtcItem(new StatSet(Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true)));
    private final EtcItem ring = new EtcItem(new StatSet(Map.of("item_id", RING_ID, "name", "Ring")));

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
    class AddItem {
        @Test
        void addItem_shouldCountAQuestItemOnce_whenTheSameItemIsAddedAgain() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item item = item(questItem);
                inventory.addItem(item);
                inventory.addItem(item);
                return inventory.getQuestSize();
            });

            assertThat(actual).isOne();
        }
    }

    @Nested
    class RemoveItem {
        @Test
        void removeItem_shouldLowerTheQuestSize_whenTheQuestItemIsHeld() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item item = item(questItem);
                inventory.addItem(item);
                inventory.removeItem(item);
                return inventory.getQuestSize();
            });

            assertThat(actual).isZero();
        }

        @Test
        void removeItem_shouldKeepTheQuestSize_whenTheItemIsNotHeld() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final int actual = WorldLoopExtension.callInPass(() -> {
                inventory.addItem(item(questItem));
                inventory.removeItem(item(questItem));
                return inventory.getQuestSize();
            });

            assertThat(actual).isOne();
        }

        @Test
        void removeItem_shouldKeepTheAdena_whenTheAdenaItemIsNotHeld() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final long actual = WorldLoopExtension.callInPass(() -> {
                inventory.addItem(ItemProcessType.NONE, stack(adena, 100), null, null);
                inventory.removeItem(stack(adena, 5));
                return inventory.getAdena();
            });

            assertThat(actual).isEqualTo(100);
        }
    }

    @Nested
    class DestroyItemByItemId {
        @Test
        void destroyItemByItemId_shouldDestroyOnlyItemsNotPending() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] pending = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                pending[0] = item(ring);
                inventory.addItem(pending[0]);
                inventory.addItem(item(ring));
                inventory.addItem(item(ring));
                return ItemContainerTest.whilePending(pending[0], () -> {
                    inventory.destroyItemByItemId(ItemProcessType.NONE, RING_ID, 2, null, null);
                    return inventory.getItems();
                });
            });

            assertThat(actual).containsExactly(pending[0]);
        }

        @Test
        void destroyItemByItemId_shouldDestroyNothing_whenOnlyPendingItemsCoverTheCount() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] destroyed = new Item[1];
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item pending = item(ring);
                inventory.addItem(pending);
                inventory.addItem(item(ring));
                destroyed[0] = ItemContainerTest.whilePending(
                        pending, () -> inventory.destroyItemByItemId(ItemProcessType.NONE, RING_ID, 2, null, null));
                return inventory.getSize();
            });

            assertThat(destroyed[0]).isNull();
            assertThat(actual).isEqualTo(2);
        }
    }

    @Nested
    class AddCommitted {
        @Test
        void addCommitted_shouldTrackTheAdena() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final long actual = WorldLoopExtension.callInPass(() -> {
                inventory.addCommitted(stack(adena, 70));
                return inventory.getAdena();
            });

            assertThat(actual).isEqualTo(70);
        }
    }

    @Nested
    class DeleteMe {
        @Test
        void deleteMe_shouldResetTheQuestSize() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final int actual = WorldLoopExtension.callInPass(() -> {
                inventory.addItem(item(questItem));
                inventory.deleteMe();
                return inventory.getQuestSize();
            });

            assertThat(actual).isZero();
        }

        @Test
        void deleteMe_shouldForgetTheAdena() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final long actual = WorldLoopExtension.callInPass(() -> {
                inventory.addItem(ItemProcessType.NONE, stack(adena, 100), null, null);
                inventory.deleteMe();
                return inventory.getAdena();
            });

            assertThat(actual).isZero();
        }
    }

    private Item item(EtcItem template) {
        return new Item(registry.allocateTransient(), template);
    }

    private Item stack(EtcItem template, long count) {
        final Item item = item(template);
        item.setCount(count);
        return item;
    }
}
