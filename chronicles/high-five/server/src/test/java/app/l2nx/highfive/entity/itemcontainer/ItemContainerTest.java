/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.itemcontainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.actor.Creature;
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
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ItemContainerTest {
    private static final int SWORD_ID = 20_000_010;
    private static final int ARROW_ID = 20_000_011;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem single = new EtcItem(new StatSet(Map.of("item_id", SWORD_ID, "name", "Single")));
    private final EtcItem stackable =
            new EtcItem(new StatSet(Map.of("item_id", ARROW_ID, "name", "Stack", "is_stackable", true)));

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
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ItemContainer> call) {
            final Bag bag = new Bag();

            assertThatThrownBy(() -> call.accept(bag))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ItemContainer>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getSize", ItemContainer::getSize),
                    Named.of("getItems", ItemContainer::getItems),
                    Named.of("collectItems", bag -> bag.collectItems(new ArrayList<>(), _ -> true)),
                    Named.of("getItemByObjectId", bag -> bag.getItemByObjectId(1)),
                    Named.of("getItemByItemId", bag -> bag.getItemByItemId(SWORD_ID)),
                    Named.of("getAllItemsByItemId", bag -> bag.getAllItemsByItemId(SWORD_ID)),
                    Named.of("getInventoryItemCount", bag -> bag.getInventoryItemCount(SWORD_ID, -1)),
                    Named.of("getAdena", ItemContainer::getAdena),
                    Named.of(
                            "destroyItemByItemId",
                            bag -> bag.destroyItemByItemId(ItemProcessType.NONE, SWORD_ID, 1, null, null)),
                    Named.of(
                            "transferItem", bag -> bag.transferItem(ItemProcessType.NONE, 1, 1, new Bag(), null, null)),
                    Named.of("destroyAllItems", bag -> bag.destroyAllItems(ItemProcessType.NONE, null, null)),
                    Named.of("deleteMe", ItemContainer::deleteMe),
                    Named.of("validateCapacity", bag -> bag.validateCapacity(1)));
        }

        @Test
        void getSize_shouldAnswer_whenCalledOnTheLoop() {
            final Bag bag = new Bag();

            final int actual = WorldLoopExtension.callInPass(bag::getSize);

            assertThat(actual).isZero();
        }
    }

    @Nested
    class GetItems {
        @Test
        void getItems_shouldReturnAReadOnlyList() {
            final Bag bag = new Bag();
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                bag.put(item(single));
                return bag.getItems();
            });

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getItems_shouldAnswerANullQuery_whenTheContainerIsEmpty() {
            final Bag bag = new Bag();

            final boolean actual =
                    WorldLoopExtension.callInPass(() -> bag.getItems().contains(null));

            assertThat(actual).isFalse();
        }

        @Test
        void getItems_shouldKeepItsEntries_whenTheItemsAreDestroyed() {
            final Bag bag = new Bag();
            final Item[] expected = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = item(single);
                bag.put(expected[0]);
                final List<Item> snapshot = bag.getItems();
                bag.destroyAllItems(ItemProcessType.NONE, null, null);
                return snapshot;
            });

            assertThat(actual).containsExactly(expected[0]);
        }

        @Test
        void getItems_shouldKeepInsertionOrder_whenObjectIdsAreNotAscending() {
            final Bag bag = new Bag();
            final List<Item> expected = new ArrayList<>();
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                for (int i = 0; i < 20; i++) {
                    expected.add(item(single));
                }

                for (int i = expected.size() - 1; i >= 0; i--) {
                    bag.put(expected.get(i));
                }

                return bag.getItems();
            });

            assertThat(actual).containsExactlyElementsOf(expected.reversed());
        }
    }

    @Nested
    class CollectItems {
        @Test
        void collectItems_shouldAppendInInsertionOrder_whenAnItemLeftAndCameBack() {
            final Bag bag = new Bag();
            final List<Item> actual = new ArrayList<>();
            final Item[] items = new Item[3];
            WorldLoopExtension.runInPass(() -> {
                for (int i = 0; i < items.length; i++) {
                    items[i] = item(single);
                    bag.put(items[i]);
                }

                bag.take(items[0]);
                bag.put(items[0]);
                bag.collectItems(actual, _ -> true);
            });

            assertThat(actual).containsExactly(items[1], items[2], items[0]);
        }

        @Test
        void collectItems_shouldAppendOnlyTheAcceptedItemsInInsertionOrder_whenFiltered() {
            final Bag bag = new Bag();
            final List<Item> actual = new ArrayList<>();
            final List<Item> expected = new ArrayList<>();
            WorldLoopExtension.runInPass(() -> {
                for (int i = 0; i < 3; i++) {
                    final Item sword = item(single);
                    bag.put(sword);
                    expected.add(sword);
                    bag.put(stack(1));
                }

                bag.collectItems(actual, item -> item.getId() == SWORD_ID);
            });

            assertThat(actual).containsExactlyElementsOf(expected);
        }

        @Test
        void collectItems_shouldKeepTheElementsTheListHeld_whenItIsNotEmpty() {
            final Bag bag = new Bag();
            final List<Object> actual = new ArrayList<>();
            final Item[] sword = new Item[1];
            WorldLoopExtension.runInPass(() -> {
                sword[0] = item(single);
                bag.put(sword[0]);
                bag.put(stack(1));
                actual.add("held");
                bag.collectItems(actual, item -> item.getId() == SWORD_ID);
            });

            assertThat(actual).containsExactly("held", sword[0]);
        }

        @Test
        void collectItems_shouldTestEveryItem_whenTheFilterRemovesALaterOne() {
            final Bag bag = new Bag();
            final List<Item> actual = new ArrayList<>();
            final Item[] items = new Item[3];
            WorldLoopExtension.runInPass(() -> {
                for (int i = 0; i < items.length; i++) {
                    items[i] = item(single);
                    bag.put(items[i]);
                }

                bag.collectItems(actual, item -> {
                    if (item == items[0]) {
                        bag.take(items[2]);
                    }

                    return true;
                });
            });

            assertThat(actual).containsExactly(items);
        }
    }

    @Nested
    class GetAllItemsByItemId {
        @Test
        void getAllItemsByItemId_shouldReturnTheItemsOfTheId_whenOthersAreHeldToo() {
            final Bag bag = new Bag();
            final Item[] expected = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = item(single);
                bag.put(expected[0]);
                bag.put(stack(1));
                return bag.getAllItemsByItemId(SWORD_ID);
            });

            assertThat(actual).containsExactly(expected[0]);
        }

        @Test
        void getAllItemsByItemId_shouldReturnAReadOnlyList() {
            final Bag bag = new Bag();
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                bag.put(item(single));
                return bag.getAllItemsByItemId(SWORD_ID);
            });

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetItemByObjectId {
        @Test
        void getItemByObjectId_shouldFindTheItem_whenTheContainerHoldsIt() {
            final Bag bag = new Bag();
            final Item[] expected = new Item[1];
            final Item actual = WorldLoopExtension.callInPass(() -> {
                bag.put(item(single));
                expected[0] = item(single);
                bag.put(expected[0]);
                return bag.getItemByObjectId(expected[0].getObjectId());
            });

            assertThat(actual).isSameAs(expected[0]);
        }

        @Test
        void getItemByObjectId_shouldBeNull_whenTheItemWasRemoved() {
            final Bag bag = new Bag();
            final Item actual = WorldLoopExtension.callInPass(() -> {
                final Item item = item(single);
                bag.put(item);
                bag.take(item);
                return bag.getItemByObjectId(item.getObjectId());
            });

            assertThat(actual).isNull();
        }
    }

    @Nested
    class DestroyItem {
        @Test
        void destroyItem_shouldLowerTheCount_whenPartOfAStackIsDestroyed() {
            final Bag bag = new Bag();
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                bag.destroyItem(ItemProcessType.NONE, arrows, 4, null, null);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(actual).isEqualTo(6);
        }

        @Test
        void destroyItem_shouldRemoveTheItem_whenTheWholeCountIsDestroyed() {
            final Bag bag = new Bag();
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                bag.put(item(single));
                bag.destroyItem(ItemProcessType.NONE, arrows, 10, null, null);
                return bag.getSize();
            });

            assertThat(actual).isEqualTo(1);
        }

        @Test
        void destroyItem_shouldKeepTheItem_whenMoreThanItsCountIsAsked() {
            final Bag bag = new Bag();
            final Item[] destroyed = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                destroyed[0] = bag.destroyItem(ItemProcessType.NONE, arrows, 11, null, null);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(destroyed[0]).isNull();
            assertThat(actual).isEqualTo(10);
        }

        @ParameterizedTest(name = "count {0}")
        @ValueSource(longs = {0, -1, -10})
        void destroyItem_shouldKeepTheItem_whenTheCountIsNotPositive(long count) {
            final Bag bag = new Bag();
            final Item[] destroyed = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                destroyed[0] = bag.destroyItem(ItemProcessType.NONE, arrows, count, null, null);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(destroyed[0]).isNull();
            assertThat(actual).isEqualTo(10);
        }

        @ParameterizedTest(name = "count {0}")
        @ValueSource(longs = {0, -1})
        void destroyItem_shouldKeepTheItem_whenTheCountIsNotPositiveForAnObjectId(long count) {
            final Bag bag = new Bag();
            final Item[] destroyed = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                destroyed[0] = bag.destroyItem(ItemProcessType.NONE, arrows.getObjectId(), count, null, null);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(destroyed[0]).isNull();
            assertThat(actual).isEqualTo(10);
        }

        @Test
        void destroyItem_shouldReturnNull_whenTheContainerDoesNotHoldTheItem() {
            final Bag bag = new Bag();
            final Item actual = WorldLoopExtension.callInPass(
                    () -> bag.destroyItem(ItemProcessType.NONE, item(single), 1, null, null));

            assertThat(actual).isNull();
        }

        @Test
        void destroyItem_shouldLeaveTheStack_whenPartOfAStackHeldElsewhereIsAsked() {
            final Bag bag = new Bag();
            final Bag other = new Bag();
            final Item[] destroyed = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                other.put(arrows);
                destroyed[0] = bag.destroyItem(ItemProcessType.NONE, arrows, 4, null, null);
                return other.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(destroyed[0]).isNull();
            assertThat(actual).isEqualTo(10);
        }
    }

    @Nested
    class DestroyItemByItemId {
        @ParameterizedTest(name = "count {0}")
        @ValueSource(longs = {0, -1})
        void destroyItemByItemId_shouldKeepTheItem_whenTheCountIsNotPositive(long count) {
            final Bag bag = new Bag();
            final Item[] destroyed = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                bag.put(stack(10));
                destroyed[0] = bag.destroyItemByItemId(ItemProcessType.NONE, ARROW_ID, count, null, null);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(destroyed[0]).isNull();
            assertThat(actual).isEqualTo(10);
        }
    }

    @Nested
    class DestroyAllItems {
        @Test
        void destroyAllItems_shouldEmptyTheContainer_whenEachDestroyRemovesFromIt() {
            final Bag bag = new Bag();
            final int actual = WorldLoopExtension.callInPass(() -> {
                for (int i = 0; i < 5; i++) {
                    bag.put(item(single));
                }

                bag.put(stack(3));
                bag.destroyAllItems(ItemProcessType.NONE, null, null);
                return bag.getSize();
            });

            assertThat(actual).isZero();
        }
    }

    @Nested
    class TransferItem {
        @Test
        void transferItem_shouldMoveTheItemItself_whenItIsNotStackable() {
            final Bag source = new Bag();
            final Bag target = new Bag();
            final Item[] sword = new Item[1];
            final Item actual = WorldLoopExtension.callInPass(() -> {
                sword[0] = item(single);
                source.put(sword[0]);
                return source.transferItem(ItemProcessType.NONE, sword[0].getObjectId(), 1, target, null, null);
            });

            assertThat(actual).isSameAs(sword[0]);
            assertThat(WorldLoopExtension.callInPass(source::getSize)).isZero();
            assertThat(WorldLoopExtension.callInPass(target::getItems)).containsExactly(sword[0]);
        }

        @Test
        void transferItem_shouldMoveTheCountIntoTheTargetStack_whenBothHoldTheStack() {
            final Bag source = new Bag();
            final Bag target = new Bag();
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                source.put(arrows);
                target.put(stack(5));
                source.transferItem(ItemProcessType.NONE, arrows.getObjectId(), 4, target, null, null);
                return new long[] {
                    source.getInventoryItemCount(ARROW_ID, -1), target.getInventoryItemCount(ARROW_ID, -1)
                };
            });

            assertThat(actual).containsExactly(6, 9);
        }

        @Test
        void transferItem_shouldCapTheCount_whenMoreThanTheStackIsAsked() {
            final Bag source = new Bag();
            final Bag target = new Bag();
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                source.put(arrows);
                target.put(stack(5));
                source.transferItem(ItemProcessType.NONE, arrows.getObjectId(), 25, target, null, null);
                return new long[] {source.getSize(), target.getInventoryItemCount(ARROW_ID, -1)};
            });

            assertThat(actual).containsExactly(0, 15);
        }

        @ParameterizedTest(name = "count {0}")
        @ValueSource(longs = {0, -1, -10})
        void transferItem_shouldMoveNothing_whenTheCountIsNotPositive(long count) {
            final Bag source = new Bag();
            final Bag target = new Bag();
            final Item[] transferred = new Item[1];
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                source.put(arrows);
                target.put(stack(5));
                transferred[0] =
                        source.transferItem(ItemProcessType.NONE, arrows.getObjectId(), count, target, null, null);
                return new long[] {
                    source.getInventoryItemCount(ARROW_ID, -1), target.getInventoryItemCount(ARROW_ID, -1)
                };
            });

            assertThat(transferred[0]).isNull();
            assertThat(actual).containsExactly(10, 5);
        }

        @Test
        void transferItem_shouldReturnNull_whenTheContainerDoesNotHoldTheObjectId() {
            final Bag source = new Bag();
            final Item actual = WorldLoopExtension.callInPass(
                    () -> source.transferItem(ItemProcessType.NONE, 1, 1, new Bag(), null, null));

            assertThat(actual).isNull();
        }
    }

    private Item item(EtcItem template) {
        return new Item(registry.allocateTransient(), template);
    }

    private Item stack(long count) {
        final Item item = item(stackable);
        item.setCount(count);
        return item;
    }

    // The container's own add and remove, without the database writes of its public add paths.
    static final class Bag extends ItemContainer {
        void put(Item item) {
            addItem(item);
        }

        void take(Item item) {
            removeItem(item);
        }

        @Override
        protected Creature getOwner() {
            return null;
        }

        @Override
        protected ItemLocation getBaseLocation() {
            return ItemLocation.INVENTORY;
        }
    }
}
