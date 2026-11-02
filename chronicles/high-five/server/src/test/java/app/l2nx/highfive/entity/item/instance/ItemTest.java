/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Clock;
import java.time.Duration;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ItemTest {
    private static final int ITEM_ID = 20_000_002;
    private static final int OWNER_ID = 268_435_456;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final ItemRepository items = mock(ItemRepository.class);
    private final EtcItem template = new EtcItem(new StatSet(Map.of("item_id", ITEM_ID, "name", "x")));

    @BeforeEach
    void createManager() {
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
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Item> call) {
            final Item item = item();

            assertThatThrownBy(() -> call.accept(item))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Item>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId (accessor)", Item::getId),
                    Named.of("getCount (accessor)", Item::getCount),
                    Named.of("getOwnerId (accessor)", Item::getOwnerId),
                    Named.of("getTemplate (accessor)", Item::getTemplate),
                    Named.of("getItemLocation (accessor)", Item::getItemLocation),
                    Named.of("getElementals (accessor)", Item::getElementals),
                    Named.of("hasElementals", Item::hasElementals),
                    Named.of("elementalCount", Item::elementalCount),
                    Named.of("setDropTime (accessor)", item -> item.setDropTime(1)),
                    Named.of("setCount", item -> item.setCount(2)),
                    Named.of("changeCount", item -> item.changeCount(ItemProcessType.NONE, 1, null, null)),
                    Named.of("setOwnerId", item -> item.setOwnerId(OWNER_ID)),
                    Named.of("setItemLocation", item -> item.setItemLocation(ItemLocation.INVENTORY)),
                    Named.of("setEnchantLevel", item -> item.setEnchantLevel(1)),
                    Named.of("setElementAttr", item -> item.setElementAttr(Elementals.FIRE, 10)),
                    Named.of("isEquipped", Item::isEquipped),
                    Named.of("isStackable", Item::isStackable),
                    Named.of("updateDatabase", Item::updateDatabase),
                    Named.of("pickupMe", item -> item.pickupMe(null)),
                    Named.of("dropMe", item -> item.dropMe(null, 0, 0, 0)),
                    Named.of("decayMe", Item::decayMe),
                    Named.of("asPlayer", Item::asPlayer),
                    Named.of("clearEnchantStats", Item::clearEnchantStats),
                    Named.of("setHeading", item -> item.setHeading(0)),
                    Named.of("restoreFromDb", _ -> Item.restoreFromDb(OWNER_ID, null)),
                    Named.of("restoreCommitted", _ -> Item.restoreCommitted(OWNER_ID, null, null, List.of())),
                    Named.of("isPending", Item::isPending),
                    Named.of("hasRow", Item::hasRow));
        }

        @Test
        void getCount_shouldAnswer_whenCalledOnTheLoop() {
            final Item item = item();

            final long actual = WorldLoopExtension.callInPass(item::getCount);

            assertThat(actual).isOne();
        }
    }

    @Nested
    class GetElementals {
        @Test
        void getElementals_shouldBeNull_whenTheItemHasNoAttribute() {
            final Item item = item();

            final Elementals[] actual = WorldLoopExtension.callInPass(item::getElementals);

            assertThat(actual).isNull();
        }

        @Test
        void getElementals_shouldKeepTheItemsAttributes_whenTheCallerWritesTheArray() {
            final Item item = item();

            final Integer actual = WorldLoopExtension.callInPass(() -> {
                item.setElementAttr(Elementals.FIRE, 10);
                item.getElementals()[0] = null;
                final Elementals fire = item.getElemental(Elementals.FIRE);
                return fire == null ? null : fire.getValue();
            });

            assertThat(actual).isEqualTo(10);
        }
    }

    @Nested
    class HasElementals {
        @ParameterizedTest(name = "{0}")
        @MethodSource("attributeChanges")
        void hasElementals_shouldFollowTheAttributes(Consumer<Item> change, boolean expected) {
            final Item item = item();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                change.accept(item);
                return item.hasElementals();
            });

            assertThat(actual).isEqualTo(expected);
        }

        static Stream<Arguments> attributeChanges() {
            return Stream.of(
                    Arguments.of(Named.<Consumer<Item>>of("no attribute", _ -> {}), false),
                    Arguments.of(
                            Named.<Consumer<Item>>of("attribute set", item -> item.setElementAttr(Elementals.FIRE, 10)),
                            true),
                    Arguments.of(
                            Named.<Consumer<Item>>of("last attribute cleared", item -> {
                                item.setElementAttr(Elementals.FIRE, 10);
                                item.clearElementAttr(Elementals.FIRE);
                            }),
                            false));
        }
    }

    @Nested
    class ElementalCount {
        @ParameterizedTest(name = "{0}")
        @MethodSource("attributeChanges")
        void elementalCount_shouldCountTheAttributes(Consumer<Item> change, int expected) {
            final Item item = item();

            final int actual = WorldLoopExtension.callInPass(() -> {
                change.accept(item);
                return item.elementalCount();
            });

            assertThat(actual).isEqualTo(expected);
        }

        static Stream<Arguments> attributeChanges() {
            return Stream.of(
                    Arguments.of(Named.<Consumer<Item>>of("no attribute", _ -> {}), 0),
                    Arguments.of(
                            Named.<Consumer<Item>>of("two attributes", item -> {
                                item.setElementAttr(Elementals.FIRE, 10);
                                item.setElementAttr(Elementals.WATER, 10);
                            }),
                            2),
                    Arguments.of(
                            Named.<Consumer<Item>>of("one attribute changed twice", item -> {
                                item.setElementAttr(Elementals.FIRE, 10);
                                item.setElementAttr(Elementals.FIRE, 20);
                            }),
                            1));
        }
    }

    @Nested
    class UpdateDatabase {
        @Test
        void updateDatabase_shouldInsertThenUpdateThenDelete_whenTheItemChangesBetweenSaves() {
            final Item item = item();
            final int objectId = WorldLoopExtension.callInPass(item::getObjectId);

            WorldLoopExtension.runInPass(() -> {
                item.setOwnerId(OWNER_ID);
                item.setItemLocation(ItemLocation.INVENTORY);
                item.updateDatabase();
                item.setCount(5);
                item.updateDatabase(true);
                item.setCount(0);
                item.updateDatabase();
            });

            verify(items).insert(eq(OWNER_ID), any(ItemRow.class));
            verify(items).update(eq(OWNER_ID), any(ItemRow.class));
            verify(items).delete(objectId);
        }

        @Test
        void updateDatabase_shouldWriteNothing_whenTheItemIsPending() {
            final Item item = item();

            WorldLoopExtension.runInPass(() -> {
                final IntList handle = IntList.of(item.getObjectId());
                WorldPartition.pendingItems().reserve(handle);
                try {
                    item.setOwnerId(OWNER_ID);
                    item.setItemLocation(ItemLocation.INVENTORY);
                    item.updateDatabase(true);
                } finally {
                    WorldPartition.pendingItems().release(handle);
                }
            });

            verifyNoInteractions(items);
        }
    }

    private Item item() {
        return WorldLoopExtension.callInPass(() -> new Item(registry.allocateTransient(), template));
    }
}
