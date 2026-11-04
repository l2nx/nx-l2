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
import app.l2nx.highfive.entity.item.enums.ItemLocation;
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
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
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
    private final EtcItem adena = new EtcItem(
            new StatSet(Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true, "weight", 1)));
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
                    return ItemContainerTest.heldItems(inventory);
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
    class PlanTake {
        @Test
        void planTake_shouldPlanTheUnequippedItemsFirst_whenWornOnesMayBeTaken() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] unequipped = new Item[1];
            final Reference2LongMap<Item> plan = new Reference2LongLinkedOpenHashMap<>();
            final long actual = WorldLoopExtension.callInPass(() -> {
                inventory.addItem(worn(item(ring)));
                unequipped[0] = item(ring);
                inventory.addItem(unequipped[0]);
                return inventory.planTake(RING_ID, 1, true, plan);
            });

            assertThat(actual).isZero();
            assertThat(plan).containsExactly(Map.entry(unequipped[0], 1L));
        }

        @Test
        void planTake_shouldReturnTheShortfallAndLeaveThePlan_whenOnlyWornOrPendingItemsCouldCoverIt() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Reference2LongMap<Item> plan = new Reference2LongLinkedOpenHashMap<>();
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item pending = item(ring);
                inventory.addItem(pending);
                inventory.addItem(worn(item(ring)));
                inventory.addItem(item(ring));
                return ItemContainerTest.whilePending(pending, () -> inventory.planTake(RING_ID, 3, false, plan));
            });

            assertThat(actual).isEqualTo(2);
            assertThat(plan).isEmpty();
        }

        @Test
        void planTake_shouldPlanOnTopOfThePlan_whenATemplateIsPlannedTwice() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] stack = new Item[1];
            final Reference2LongMap<Item> plan = new Reference2LongLinkedOpenHashMap<>();
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                stack[0] = stack(adena, 10);
                inventory.addItem(stack[0]);
                return new long[] {
                    inventory.planTake(Inventory.ADENA_ID, 6, false, plan),
                    inventory.planTake(Inventory.ADENA_ID, 6, false, plan),
                    inventory.planTake(Inventory.ADENA_ID, 4, false, plan)
                };
            });

            assertThat(actual).containsExactly(0, 2, 0);
            assertThat(plan).containsExactly(Map.entry(stack[0], 10L));
        }

        @Test
        void planTake_shouldPlanEveryFreeItem_whenTheCountIsNegative() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] held = new Item[2];
            final Reference2LongMap<Item> plan = new Reference2LongLinkedOpenHashMap<>();
            final long actual = WorldLoopExtension.callInPass(() -> {
                held[0] = worn(item(ring));
                held[1] = item(ring);
                inventory.addItem(held[0]);
                inventory.addItem(held[1]);
                return inventory.planTake(RING_ID, -1, true, plan);
            });

            assertThat(actual).isZero();
            assertThat(plan).containsExactly(Map.entry(held[1], 1L), Map.entry(held[0], 1L));
        }
    }

    @Nested
    class Pending {
        @Test
        void adenaQueries_shouldLeaveOutThePendingStack_whileTheIncludingOnesSeeIt() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] stack = new Item[1];
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                stack[0] = stack(adena, 100);
                inventory.addItem(ItemProcessType.NONE, stack[0], null, null);
                return ItemContainerTest.whilePending(stack[0], () -> new Object[] {
                    inventory.getAdena(),
                    inventory.getAdenaInstance(),
                    inventory.getAdenaIncludingPending(),
                    inventory.getAdenaInstanceIncludingPending()
                });
            });

            assertThat(actual).containsExactly(0L, null, 100L, stack[0]);
        }

        @Test
        void getTotalWeight_shouldCountThePendingStack() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = stack(adena, 100);
                inventory.addItem(ItemProcessType.NONE, stack, null, null);
                return ItemContainerTest.whilePending(stack, () -> {
                    inventory.refreshWeight();
                    return inventory.getTotalWeight();
                });
            });

            assertThat(actual).isEqualTo(100);
        }

        @Test
        void getUniqueItems_shouldLeaveOutThePendingItem() {
            final PlayerInventory inventory = new PlayerInventory(mock(Player.class));
            final Item[] free = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                final Item pending = item(ring);
                free[0] = stack(adena, 100);
                inventory.addItem(pending);
                inventory.addItem(free[0]);
                return ItemContainerTest.whilePending(
                        pending, () -> inventory.getUniqueItems(true, true, false, false));
            });

            assertThat(actual).containsExactly(free[0]);
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

    private static Item worn(Item item) {
        item.setItemLocation(ItemLocation.PAPERDOLL, Inventory.PAPERDOLL_RFINGER, false);
        return item;
    }

    private Item stack(EtcItem template, long count) {
        final Item item = item(template);
        item.setCount(count);
        return item;
    }
}
