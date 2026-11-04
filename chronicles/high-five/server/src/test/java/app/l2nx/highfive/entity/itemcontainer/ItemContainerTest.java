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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
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
    private final ItemRepository items = mock(ItemRepository.class);

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
                items,
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
                    Named.of(
                            "collectItemsIncludingPending",
                            bag -> bag.collectItemsIncludingPending(new ArrayList<>(), _ -> true)),
                    Named.of("getItemByObjectId", bag -> bag.getItemByObjectId(1)),
                    Named.of("getItemIncludingPending", bag -> bag.getItemIncludingPending(1)),
                    Named.of("getItemByItemId", bag -> bag.getItemByItemId(SWORD_ID)),
                    Named.of("getItemByItemIdIncludingPending", bag -> bag.getItemByItemIdIncludingPending(SWORD_ID)),
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

    @Nested
    class Pending {
        @ParameterizedTest(name = "{0}")
        @MethodSource("mutations")
        void mutation_shouldChangeNothing_whenTheItemIsPending(BiFunction<Bag, Item, Item> mutation) {
            final Bag bag = new Bag();
            final Item[] result = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                result[0] = whilePending(arrows, () -> mutation.apply(bag, arrows));
                return arrows.getCount();
            });

            assertThat(result[0]).isNull();
            assertThat(actual).isEqualTo(10);
            verifyNoInteractions(items);
        }

        @Test
        void getInventoryItemCount_shouldLeaveOutThePendingStack_untilItsReservationEnds() {
            final Bag bag = new Bag();
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                final long during = whilePending(arrows, () -> bag.getInventoryItemCount(ARROW_ID, -1));
                return new long[] {during, bag.getInventoryItemCount(ARROW_ID, -1)};
            });

            assertThat(actual).containsExactly(0, 10);
        }

        @Test
        void getInventoryItemCount_shouldCountOnlyTheFreeItems_whenOneOfSeveralIsPending() {
            final Bag bag = new Bag();
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item pending = item(single);
                bag.put(pending);
                bag.put(item(single));
                return whilePending(pending, () -> bag.getInventoryItemCount(SWORD_ID, -1));
            });

            assertThat(actual).isOne();
        }

        static Stream<Named<BiFunction<Bag, Item, Item>>> mutations() {
            return Stream.of(
                    Named.of("destroyItem", (bag, item) -> bag.destroyItem(ItemProcessType.NONE, item, null, null)),
                    Named.of(
                            "destroyItem part",
                            (bag, item) -> bag.destroyItem(ItemProcessType.NONE, item, 4, null, null)),
                    Named.of(
                            "destroyItem by object id",
                            (bag, item) -> bag.destroyItem(ItemProcessType.NONE, item.getObjectId(), 4, null, null)),
                    Named.of(
                            "destroyItemByItemId",
                            (bag, item) -> bag.destroyItemByItemId(ItemProcessType.NONE, ARROW_ID, 4, null, null)),
                    Named.of(
                            "transferItem",
                            (bag, item) -> bag.transferItem(
                                    ItemProcessType.NONE, item.getObjectId(), 4, new Bag(), null, null)));
        }

        @Test
        void transferItem_shouldMergeIntoTheTargetStack_whenItIsPending() {
            final Bag source = new Bag();
            final Bag target = new Bag();
            final Item[] held = new Item[1];
            final Item[] transferred = new Item[1];
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                held[0] = stack(5);
                source.put(arrows);
                target.put(held[0]);
                transferred[0] = whilePending(
                        held[0],
                        () -> source.transferItem(ItemProcessType.NONE, arrows.getObjectId(), 4, target, null, null));
                return new long[] {
                    source.getInventoryItemCount(ARROW_ID, -1), target.getInventoryItemCount(ARROW_ID, -1)
                };
            });

            assertThat(transferred[0]).isSameAs(held[0]);
            assertThat(actual).containsExactly(6, 9);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("gameplayLookups")
        void gameplayLookup_shouldNotFindThePendingItem(BiFunction<Bag, Item, Item> lookup) {
            final Bag bag = new Bag();
            final Item actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                return whilePending(arrows, () -> lookup.apply(bag, arrows));
            });

            assertThat(actual).isNull();
        }

        static Stream<Named<BiFunction<Bag, Item, Item>>> gameplayLookups() {
            return Stream.of(
                    Named.of("getItemByObjectId", (bag, item) -> bag.getItemByObjectId(item.getObjectId())),
                    Named.of("getItemByItemId", (bag, item) -> bag.getItemByItemId(ARROW_ID)),
                    Named.of("getItemByItemId ignoring", (bag, item) -> bag.getItemByItemId(ARROW_ID, null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("gameplayLists")
        void gameplayList_shouldLeaveOutThePendingItem(Function<Bag, List<Item>> list) {
            final Bag bag = new Bag();
            final Item[] free = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                final Item pending = stack(10);
                free[0] = item(single);
                bag.put(pending);
                bag.put(free[0]);
                return whilePending(pending, () -> list.apply(bag));
            });

            assertThat(actual).containsExactly(free[0]);
        }

        static Stream<Named<Function<Bag, List<Item>>>> gameplayLists() {
            return Stream.of(
                    Named.of("getItems", ItemContainer::getItems),
                    Named.of("collectItems", bag -> {
                        final List<Item> into = new ArrayList<>();
                        bag.collectItems(into, _ -> true);
                        return into;
                    }),
                    Named.of("getAllItemsByItemId of both", bag -> {
                        final List<Item> into = new ArrayList<>(bag.getAllItemsByItemId(ARROW_ID));
                        into.addAll(bag.getAllItemsByItemId(SWORD_ID));
                        return into;
                    }));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("heldLists")
        void includingPendingQuery_shouldSeeThePendingItem(Function<Bag, List<Item>> list) {
            final Bag bag = new Bag();
            final Item[] pending = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                pending[0] = stack(10);
                bag.put(pending[0]);
                return whilePending(pending[0], () -> list.apply(bag));
            });

            assertThat(actual).containsExactly(pending[0]);
        }

        static Stream<Named<Function<Bag, List<Item>>>> heldLists() {
            return Stream.of(
                    Named.of("getItemIncludingPending", bag -> List.of(bag.getItemIncludingPending(handleOf(bag)))),
                    Named.of(
                            "getItemByItemIdIncludingPending",
                            bag -> List.of(bag.getItemByItemIdIncludingPending(ARROW_ID))),
                    Named.of("collectItemsIncludingPending", ItemContainerTest::heldItems));
        }

        @Test
        void getSize_shouldCountThePendingItem() {
            final Bag bag = new Bag();
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                return whilePending(arrows, bag::getSize);
            });

            assertThat(actual).isOne();
        }

        @Test
        void addItem_shouldMergeIntoThePendingStack() {
            final Bag bag = new Bag();
            final Item[] pending = new Item[1];
            final Item[] added = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                pending[0] = stack(10);
                bag.put(pending[0]);
                added[0] = whilePending(pending[0], () -> bag.addItem(ItemProcessType.NONE, ARROW_ID, 5, null, null));
                return pending[0].getCount();
            });

            assertThat(added[0]).isSameAs(pending[0]);
            assertThat(actual).isEqualTo(15);
        }

        @Test
        void destroyItemByItemId_shouldDestroyAFreeInstance_whenAnotherIsPending() {
            final Bag bag = new Bag();
            final Item[] pending = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                pending[0] = item(single);
                bag.put(pending[0]);
                bag.put(item(single));
                return whilePending(pending[0], () -> {
                    bag.destroyItemByItemId(ItemProcessType.NONE, SWORD_ID, 1, null, null);
                    return heldItems(bag);
                });
            });

            assertThat(actual).containsExactly(pending[0]);
        }

        @Test
        void gameplayWalk_shouldSkipThePendingCheck_whenNothingIsPending() {
            final Bag bag = new Bag();
            final Item[] free = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                free[0] = spy(stack(10));
                bag.put(free[0]);
                return bag.getItems();
            });

            assertThat(actual).containsExactly(free[0]);
            verify(free[0], never()).isPending();
        }

        @Test
        void gameplayWalk_shouldTestTheFilterBeforeThePendingCheck_whenAnItemIsPending() {
            final Bag bag = new Bag();
            final Item[] free = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                final Item pending = item(single);
                free[0] = spy(stack(10));
                bag.put(pending);
                bag.put(free[0]);
                return whilePending(pending, () -> bag.getAllItemsByItemId(SWORD_ID));
            });

            assertThat(actual).isEmpty();
            verify(free[0], never()).isPending();
        }

        @Test
        void destroyAllItems_shouldKeepThePendingItem() {
            final Bag bag = new Bag();
            final Item[] pending = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                pending[0] = item(single);
                bag.put(pending[0]);
                bag.put(stack(3));
                return whilePending(pending[0], () -> {
                    bag.destroyAllItems(ItemProcessType.NONE, null, null);
                    return heldItems(bag);
                });
            });

            assertThat(actual).containsExactly(pending[0]);
        }
    }

    @Nested
    class Committed {
        @Test
        void changeCommitted_shouldChangeTheCountWithoutWriting() {
            final Bag bag = new Bag();
            final boolean[] changed = new boolean[2];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                changed[0] = bag.changeCommitted(arrows, -4);
                changed[1] = bag.changeCommitted(arrows, 7);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(changed).containsExactly(true, true);
            assertThat(actual).isEqualTo(13);
            verifyNoInteractions(items);
        }

        @ParameterizedTest(name = "delta {0}")
        @ValueSource(longs = {-10, -11})
        void changeCommitted_shouldRefuse_whenNothingWouldBeLeft(long delta) {
            final Bag bag = new Bag();
            final boolean[] changed = new boolean[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                changed[0] = bag.changeCommitted(arrows, delta);
                return bag.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(changed[0]).isFalse();
            assertThat(actual).isEqualTo(10);
        }

        @Test
        void removeCommitted_shouldTakeTheItemOutWithoutWriting() {
            final Bag bag = new Bag();
            final boolean[] removed = new boolean[1];
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = stack(10);
                bag.put(arrows);
                removed[0] = bag.removeCommitted(arrows);
                return bag.getSize();
            });

            assertThat(removed[0]).isTrue();
            assertThat(actual).isZero();
            verifyNoInteractions(items);
        }

        @Test
        void removeCommitted_shouldRefuse_whenTheContainerDoesNotHoldTheItem() {
            final Bag bag = new Bag();
            final boolean actual = WorldLoopExtension.callInPass(() -> bag.removeCommitted(stack(10)));

            assertThat(actual).isFalse();
        }

        @Test
        void addCommitted_shouldPutTheItemInWithoutWriting() {
            final Bag bag = new Bag();
            final Item[] expected = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = stack(10);
                bag.addCommitted(expected[0]);
                return bag.getItems();
            });

            assertThat(actual).containsExactly(expected[0]);
            verifyNoInteractions(items);
        }
    }

    private Item item(EtcItem template) {
        return new Item(registry.allocateTransient(), template);
    }

    private static int handleOf(Bag bag) {
        return heldItems(bag).getFirst().getObjectId();
    }

    static List<Item> heldItems(ItemContainer container) {
        final List<Item> into = new ArrayList<>();
        container.collectItemsIncludingPending(into, _ -> true);
        return into;
    }

    static <T> T whilePending(Item item, Supplier<T> body) {
        final IntList handle = IntList.of(item.getObjectId());
        WorldPartition.pendingItems().reserve(handle);
        try {
            return body.get();
        } finally {
            WorldPartition.pendingItems().release(handle);
        }
    }

    private Item stack(long count) {
        final Item item = item(stackable);
        item.setCount(count);
        return item;
    }

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
