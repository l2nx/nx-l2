/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.Reservation;
import app.l2nx.engine.mail.ReserveResult;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.EnchantItemOptionsData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerWarehouse;
import app.l2nx.highfive.mail.repository.MailItemRow;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("PlayerConfig")
class WorldMailItemsTest {
    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    private static final int SWORD_ID = 20_000_050;
    private static final int ARROW_ID = 20_000_051;
    private static final int QUEST_ID = 20_000_052;
    private static final int BOUND_ID = 20_000_053;
    private static final int HEAVY_ID = 20_000_054;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final WorldMailItems mailItems = new WorldMailItems(registry);
    private final ItemRepository itemRows = mock(ItemRepository.class);
    private final UUID characterId = EntityIds.next();
    private final int handle = registry.handleOf(characterId);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private final PlayerWarehouse warehouse = new PlayerWarehouse(player);
    private final Map<Integer, ItemTemplate> templates = Map.of(
            SWORD_ID,
            template(SWORD_ID, Map.of()),
            ARROW_ID,
            template(ARROW_ID, Map.of("is_stackable", true)),
            Inventory.ADENA_ID,
            template(Inventory.ADENA_ID, Map.of("is_stackable", true)),
            QUEST_ID,
            template(QUEST_ID, Map.of("is_questitem", true)),
            BOUND_ID,
            template(BOUND_ID, Map.of("is_tradable", false)),
            HEAVY_ID,
            template(HEAVY_ID, Map.of("weight", 50)));
    private MockedStatic<World> world;
    private MockedStatic<ItemData> itemData;
    private MockedStatic<EnchantItemOptionsData> enchantOptions;
    private long maxAdena;

    @BeforeEach
    void setUp() {
        new IdManager(registry);
        final TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
                    invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                    return null;
                })
                .when(transactions)
                .executeWithoutResult(any());
        new ItemManager(
                itemRows,
                mock(ItemAugmentationRepository.class),
                mock(ItemElementalRepository.class),
                mock(ItemVariableRepository.class),
                transactions);

        when(player.getObjectId()).thenReturn(handle);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWarehouse()).thenReturn(warehouse);
        when(player.getInventoryLimit()).thenReturn(10);
        when(player.getMaxLoad()).thenReturn(100);
        when(player.checkItemManipulation(anyInt(), anyLong(), anyString())).thenAnswer(call -> {
            final Item item = inventory.getItemByObjectId(call.getArgument(0));
            final long count = call.getArgument(1);
            return ((item == null) || (count > item.getCount()) || ((count > 1) && !item.isStackable())) ? null : item;
        });
        world = mockStatic(World.class);
        world.when(() -> World.getPlayer(handle)).thenReturn(player);
        final ItemData data = mock(ItemData.class);
        when(data.getTemplate(anyInt())).thenAnswer(call -> templates.get(call.<Integer>getArgument(0)));
        itemData = mockStatic(ItemData.class);
        itemData.when(ItemData::getInstance).thenReturn(data);
        enchantOptions = mockStatic(EnchantItemOptionsData.class);
        enchantOptions.when(EnchantItemOptionsData::getInstance).thenReturn(mock(EnchantItemOptionsData.class));
        maxAdena = PlayerConfig.MAX_ADENA;
        PlayerConfig.MAX_ADENA = 1_000_000;
    }

    @AfterEach
    void tearDown() {
        world.close();
        itemData.close();
        enchantOptions.close();
        PlayerConfig.MAX_ADENA = maxAdena;
    }

    @Nested
    class Mailable {
        @Test
        void mailable_shouldListTradeableItems_andThePendingOnesThatReserveAnswersBusy() {
            final int[] expected = new int[3];
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = stored(SWORD_ID, 1).getObjectId();
                expected[1] = stored(Inventory.ADENA_ID, 1_000).getObjectId();
                stored(QUEST_ID, 1);
                stored(BOUND_ID, 1);
                expected[2] = stored(SWORD_ID, 1).getObjectId();
                WorldPartition.pendingItems().reserve(IntList.of(expected[2]));
                return new Object[] {
                    mailItems.mailable(handle), mailItems.reserve(handle, List.of(new ItemCount(expected[2], 1)), 100)
                };
            });

            assertThat((List<Integer>) actual[0]).containsExactly(expected[0], expected[1], expected[2]);
            assertThat(actual[1]).isEqualTo(new ReserveResult.Refused<>(MailRefusal.BUSY));
        }
    }

    @Nested
    class Reserve {
        @Test
        void reserve_shouldMarkTheItemsAndTheFeePendingAndCaptureTheirRows() {
            final Item[] items = new Item[2];
            final ReserveResult<Reservation> actual = WorldLoopExtension.callInPass(() -> {
                items[0] = stored(SWORD_ID, 1);
                items[1] = stored(Inventory.ADENA_ID, 1_000);
                return mailItems.reserve(handle, List.of(new ItemCount(items[0].getObjectId(), 1)), 100);
            });

            assertThat(actual).isInstanceOf(ReserveResult.Reserved.class);
            final Reservation reservation = ((ReserveResult.Reserved<Reservation>) actual).value();
            assertThat(reservation.items())
                    .singleElement()
                    .satisfies(item -> assertThat(item.row()).isEqualTo(rowOf(items[0], 1)));
            assertThat(reservation.adena()).isEqualTo(rowOf(items[1], 1_000));
            assertThat(reservation.adenaAmount()).isEqualTo(100);
            assertThat(reservation.snapshots())
                    .singleElement()
                    .satisfies(snapshot -> assertThat(snapshot.itemTemplateId()).isEqualTo(SWORD_ID));
            assertThat(pending(items[0], items[1])).containsExactly(true, true);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("notMailable")
        void reserve_shouldRefuseTheItem_whenItIsNotMailable(Function<Item[], List<ItemCount>> attachments) {
            final Item[] items = new Item[1];
            final ReserveResult<Reservation> actual = WorldLoopExtension.callInPass(() -> {
                stored(Inventory.ADENA_ID, 1_000);
                items[0] = stored(ARROW_ID, 10);
                stored(QUEST_ID, 1);
                stored(BOUND_ID, 1);
                return mailItems.reserve(handle, attachments.apply(items), 100);
            });

            assertThat(actual).isEqualTo(new ReserveResult.Refused<>(MailRefusal.ITEM_NOT_MAILABLE));
        }

        static Stream<Named<Function<Item[], List<ItemCount>>>> notMailable() {
            return Stream.of(
                    Named.of("not owned", items -> List.of(new ItemCount(items[0].getObjectId() + 1_000, 1))),
                    Named.of("more than owned", items -> List.of(new ItemCount(items[0].getObjectId(), 11))),
                    Named.of("no count", items -> List.of(new ItemCount(items[0].getObjectId(), 0))),
                    Named.of(
                            "twice",
                            items -> List.of(
                                    new ItemCount(items[0].getObjectId(), 2),
                                    new ItemCount(items[0].getObjectId(), 3))),
                    Named.of("quest item", items -> List.of(new ItemCount(items[0].getObjectId() + 1, 1))),
                    Named.of("not tradeable", items -> List.of(new ItemCount(items[0].getObjectId() + 2, 1))));
        }

        @Test
        void reserve_shouldRefuse_whenTheFeeNeedsTheAttachedAdena() {
            final Item[] adena = new Item[1];
            final ReserveResult<Reservation> actual = WorldLoopExtension.callInPass(() -> {
                adena[0] = stored(Inventory.ADENA_ID, 1_000);
                return mailItems.reserve(handle, List.of(new ItemCount(adena[0].getObjectId(), 950)), 100);
            });

            assertThat(actual).isEqualTo(new ReserveResult.Refused<>(MailRefusal.NOT_ENOUGH_ADENA));
            assertThat(pending(adena[0])).containsExactly(false);
        }

        @Test
        void reserve_shouldReserveNothing_whenOneOfTheItemsIsPending() {
            final Item[] items = new Item[3];
            final ReserveResult<Reservation> actual = WorldLoopExtension.callInPass(() -> {
                items[0] = stored(SWORD_ID, 1);
                items[1] = stored(SWORD_ID, 1);
                items[2] = stored(Inventory.ADENA_ID, 1_000);
                WorldPartition.pendingItems().reserve(IntList.of(items[1].getObjectId()));
                return mailItems.reserve(
                        handle,
                        List.of(new ItemCount(items[0].getObjectId(), 1), new ItemCount(items[1].getObjectId(), 1)),
                        100);
            });

            assertThat(actual).isEqualTo(new ReserveResult.Refused<>(MailRefusal.BUSY));
            assertThat(pending(items)).containsExactly(false, true, false);
        }

        @Test
        void reserve_shouldCarryTheWholeRow_whenTheItemAndTheFeeHaveNoRowYet() {
            final Reservation actual = WorldLoopExtension.callInPass(() -> {
                final Item fresh = new Item(registry.allocateTransient(), templates.get(SWORD_ID));
                inventory.addItem(ItemProcessType.NONE, fresh, null, null);
                fresh.setEnchantLevel(4);
                inventory.addItem(ItemProcessType.NONE, Inventory.ADENA_ID, 1_000, null, null);
                return reserved(List.of(new ItemCount(fresh.getObjectId(), 1)), 100);
            });

            assertThat(actual.items().getFirst().row().unstoredRow()).isInstanceOfSatisfying(MailItemRow.class, row -> {
                assertThat(row.itemId())
                        .isEqualTo(actual.items().getFirst().row().itemId());
                assertThat(row.ownerId()).isEqualTo(characterId);
                assertThat(row.location()).isEqualTo(ItemLocation.INVENTORY);
                assertThat(row.locationSlot()).isZero();
                assertThat(row.itemTemplateId()).isEqualTo(SWORD_ID);
                assertThat(row.count()).isOne();
                assertThat(row.enchantLevel()).isEqualTo(4);
            });
            assertThat(actual.adena().unstoredRow())
                    .isInstanceOfSatisfying(
                            MailItemRow.class, row -> assertThat(row.count()).isEqualTo(1_000));
        }

        @Test
        void reserve_shouldCarryNoRow_whenTheItemIsStored() {
            final Reservation actual = WorldLoopExtension.callInPass(() -> {
                final Item sword = stored(SWORD_ID, 1);
                stored(Inventory.ADENA_ID, 1_000);
                return reserved(List.of(new ItemCount(sword.getObjectId(), 1)), 100);
            });

            assertThat(actual.items().getFirst().row().unstoredRow()).isNull();
            assertThat(actual.adena().unstoredRow()).isNull();
        }
    }

    @Nested
    class Release {
        @Test
        void release_shouldUnmarkTheReservedItems() {
            final Item[] items = new Item[2];
            final boolean[] actual = WorldLoopExtension.callInPass(() -> {
                items[0] = stored(SWORD_ID, 1);
                items[1] = stored(Inventory.ADENA_ID, 1_000);
                mailItems.release(reserved(List.of(new ItemCount(items[0].getObjectId(), 1)), 100));
                return pending(items);
            });

            assertThat(actual).containsExactly(false, false);
        }
    }

    @Nested
    class ApplyOutgoing {
        @Test
        void applyOutgoing_shouldTakeTheItemsAndTheFeeOutOfMemoryWithoutWriting() {
            final Item[] items = new Item[3];
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                items[0] = stored(SWORD_ID, 1);
                items[1] = stored(ARROW_ID, 10);
                items[2] = stored(Inventory.ADENA_ID, 1_000);
                mailItems.applyOutgoing(reserved(
                        List.of(
                                new ItemCount(items[0].getObjectId(), 1),
                                new ItemCount(items[1].getObjectId(), 4),
                                new ItemCount(items[2].getObjectId(), 300)),
                        100));
                return new long[] {
                    inventory.getInventoryItemCount(SWORD_ID, -1),
                    inventory.getInventoryItemCount(ARROW_ID, -1),
                    inventory.getAdena()
                };
            });

            assertThat(actual).containsExactly(0, 6, 600);
            assertThat(pending(items)).containsExactly(false, false, false);
            verifyNoInteractions(itemRows);
        }

        @Test
        void applyOutgoing_shouldMarkTheRowStored_whenTheTransactionInsertedIt() {
            final Item[] arrows = new Item[1];
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                arrows[0] = inventory.addItem(ItemProcessType.NONE, ARROW_ID, 10, null, null);
                stored(Inventory.ADENA_ID, 1_000);
                final boolean before = arrows[0].hasRow();
                mailItems.applyOutgoing(reserved(List.of(new ItemCount(arrows[0].getObjectId(), 4)), 100));
                return new Object[] {before, arrows[0].hasRow(), arrows[0].getCount()};
            });

            assertThat(actual).containsExactly(false, true, 6L);
            verifyNoInteractions(itemRows);
        }

        @Test
        void applyOutgoing_shouldKeepWhatTheStackTookInMeanwhile_whenTheWholeRowLeft() {
            final Item[] arrows = new Item[2];
            final long actual = WorldLoopExtension.callInPass(() -> {
                arrows[0] = stored(ARROW_ID, 10);
                stored(Inventory.ADENA_ID, 1_000);
                final Reservation reservation = reserved(List.of(new ItemCount(arrows[0].getObjectId(), 10)), 100);
                inventory.addItem(ItemProcessType.NONE, ARROW_ID, 5, null, null);
                mailItems.applyOutgoing(reservation);
                arrows[1] = inventory.getItemByItemId(ARROW_ID);
                return arrows[1].getCount();
            });

            assertThat(actual).isEqualTo(5);
            assertThat(arrows[1]).isNotSameAs(arrows[0]);
            assertThat(WorldLoopExtension.callInPass(arrows[1]::hasRow)).isFalse();
        }
    }

    @Nested
    class IncomingTargetsOf {
        @Test
        void incomingTargets_shouldMarkTheStacksTheItemsMergeIntoAndThePayingAdena() {
            final Item[] items = new Item[2];
            final ReserveResult<IncomingTargets> actual = WorldLoopExtension.callInPass(() -> {
                items[0] = stored(ARROW_ID, 10);
                items[1] = stored(Inventory.ADENA_ID, 1_000);
                return mailItems.incomingTargets(
                        handle, HolderType.INVENTORY, List.of(snapshot(ARROW_ID, 5), snapshot(SWORD_ID, 1)), -300);
            });

            assertThat(actual)
                    .isEqualTo(new ReserveResult.Reserved<>(IncomingTargets.builder()
                            .characterHandle(handle)
                            .characterId(characterId)
                            .stacks(List.of(rowOf(items[0], 10)))
                            .adena(rowOf(items[1], 1_000))
                            .build()));
            assertThat(pending(items)).containsExactly(true, true);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusals")
        void incomingTargets_shouldRefuse_whenThereIsNoRoom(Refusal refusal) {
            final Item[] adena = new Item[1];
            final ReserveResult<IncomingTargets> actual = WorldLoopExtension.callInPass(() -> {
                adena[0] = stored(Inventory.ADENA_ID, 1_000);
                return mailItems.incomingTargets(handle, HolderType.INVENTORY, refusal.items(), refusal.adena());
            });

            assertThat(actual).isEqualTo(new ReserveResult.Refused<>(refusal.expected()));
            assertThat(pending(adena[0])).containsExactly(false);
        }

        static Stream<Named<Refusal>> refusals() {
            return Stream.of(
                    Named.of(
                            "more items than slots",
                            new Refusal(Collections.nCopies(10, snapshot(SWORD_ID, 1)), 0, MailRefusal.INVENTORY_FULL)),
                    Named.of(
                            "too heavy",
                            new Refusal(
                                    List.of(snapshot(HEAVY_ID, 1), snapshot(HEAVY_ID, 1), snapshot(HEAVY_ID, 1)),
                                    0,
                                    MailRefusal.WEIGHT_LIMIT)),
                    Named.of("cannot pay", new Refusal(List.of(), -1_001, MailRefusal.NOT_ENOUGH_ADENA)),
                    Named.of(
                            "attached adena cannot pay",
                            new Refusal(
                                    List.of(snapshot(Inventory.ADENA_ID, 500)), -1_200, MailRefusal.NOT_ENOUGH_ADENA)),
                    Named.of("adena above the limit", new Refusal(List.of(), 999_001, MailRefusal.ADENA_LIMIT)));
        }

        @Test
        void incomingTargets_shouldAwaitTheNewStacks_andKeepAStackOfThemFromBeingStored() {
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                final IncomingTargets targets = targets(List.of(snapshot(ARROW_ID, 5), snapshot(SWORD_ID, 1)), 700);
                final Item arrows = inventory.addItem(ItemProcessType.NONE, ARROW_ID, 3, null, null);
                final boolean pending = arrows.isPending();
                arrows.updateDatabase(true);
                final Item destroyed = inventory.destroyItem(ItemProcessType.NONE, arrows, null, null);
                mailItems.release(targets);
                return new Object[] {targets.newStackTemplates(), pending, destroyed, arrows.isPending()};
            });

            assertThat((List<Integer>) actual[0]).containsExactlyInAnyOrder(ARROW_ID, Inventory.ADENA_ID);
            assertThat(actual[1]).isEqualTo(true);
            assertThat(actual[2]).isNull();
            assertThat(actual[3]).isEqualTo(false);
            verifyNoInteractions(itemRows);
        }

        @Test
        void incomingTargets_shouldMarkTheWarehouseStacks_whenAttachmentsExpireIntoIt() {
            final Item[] stack = new Item[1];
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                stored(ARROW_ID, 30);
                stack[0] = Item.restoreFromDb(handle, itemRow(ARROW_ID, 7, ItemLocation.WAREHOUSE));
                warehouse.addCommitted(stack[0]);
                final ReserveResult<IncomingTargets> targets = mailItems.incomingTargets(
                        handle, HolderType.WAREHOUSE, List.of(snapshot(ARROW_ID, 5), snapshot(SWORD_ID, 1)), 0);
                final Item withdrawn = warehouse.transferItem(
                        ItemProcessType.TRANSFER, stack[0].getObjectId(), 7, inventory, player, null);
                return new Object[] {targets, withdrawn, warehouse.getInventoryItemCount(ARROW_ID, -1)};
            });

            assertThat(actual[0])
                    .isEqualTo(new ReserveResult.Reserved<>(IncomingTargets.builder()
                            .characterHandle(handle)
                            .characterId(characterId)
                            .stacks(List.of(rowOf(stack[0], 7)))
                            .build()));
            assertThat(actual[1]).isNull();
            assertThat(actual[2]).isEqualTo(7L);
        }

        @Test
        void incomingTargets_shouldTakeAWarehouseFullOfItems() {
            final ReserveResult<IncomingTargets> actual = WorldLoopExtension.callInPass(() -> mailItems.incomingTargets(
                    handle, HolderType.WAREHOUSE, Collections.nCopies(400, snapshot(HEAVY_ID, 1)), 0));

            assertThat(actual).isInstanceOf(ReserveResult.Reserved.class);
        }

        @Test
        void incomingTargets_shouldCarryTheWholeRow_whenTheStackAndThePayingAdenaHaveNoRowYet() {
            final IncomingTargets actual = WorldLoopExtension.callInPass(() -> {
                inventory.addItem(ItemProcessType.NONE, ARROW_ID, 3, null, null);
                inventory.addItem(ItemProcessType.NONE, Inventory.ADENA_ID, 500, null, null);
                return targets(List.of(snapshot(ARROW_ID, 5)), -300);
            });

            assertThat(actual.stacks())
                    .singleElement()
                    .satisfies(stack -> assertThat(stack.unstoredRow()).isNotNull());
            assertThat(actual.adena().unstoredRow()).isNotNull();
        }

        @Test
        void incomingTargets_shouldRefuse_whenTheStackIsPending() {
            final Item[] arrows = new Item[1];
            final ReserveResult<IncomingTargets> actual = WorldLoopExtension.callInPass(() -> {
                arrows[0] = stored(ARROW_ID, 10);
                WorldPartition.pendingItems().reserve(IntList.of(arrows[0].getObjectId()));
                return mailItems.incomingTargets(handle, HolderType.INVENTORY, List.of(snapshot(ARROW_ID, 5)), 0);
            });

            assertThat(actual).isEqualTo(new ReserveResult.Refused<>(MailRefusal.BUSY));
        }
    }

    @Nested
    class ApplyIncoming {
        @Test
        void applyIncoming_shouldMergeByWhatTheRowTook_whenTheStackChangedMeanwhile() {
            final Item[] arrows = new Item[1];
            final long actual = WorldLoopExtension.callInPass(() -> {
                arrows[0] = stored(ARROW_ID, 10);
                final IncomingTargets targets = targets(List.of(snapshot(ARROW_ID, 5)), 0);
                inventory.addItem(ItemProcessType.NONE, ARROW_ID, 3, null, null);
                mailItems.applyIncoming(targets, List.of(committed(arrows[0], ARROW_ID, 15, ItemLocation.INVENTORY)));
                return inventory.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(actual).isEqualTo(18);
            assertThat(pending(arrows[0])).containsExactly(false);
            verifyNoInteractions(itemRows);
        }

        @Test
        void applyIncoming_shouldApplyTheLastState_whenARowWasPaidFromAndMergedInto() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item adena = stored(Inventory.ADENA_ID, 1_000);
                final IncomingTargets targets = targets(List.of(snapshot(Inventory.ADENA_ID, 200)), -300);
                mailItems.applyIncoming(
                        targets,
                        List.of(
                                committed(adena, Inventory.ADENA_ID, 700, ItemLocation.INVENTORY),
                                committed(adena, Inventory.ADENA_ID, 900, ItemLocation.INVENTORY)));
                return inventory.getAdena();
            });

            assertThat(actual).isEqualTo(900);
        }

        @Test
        void applyIncoming_shouldPutANewRowInWithItsAugmentationAndElements() {
            final UUID itemId = EntityIds.next();
            final Item actual = WorldLoopExtension.callInPass(() -> {
                mailItems.applyIncoming(
                        noTargets(),
                        List.of(MailItemRow.builder()
                                .itemId(itemId)
                                .ownerId(characterId)
                                .location(ItemLocation.INVENTORY)
                                .locationSlot(0)
                                .itemTemplateId(SWORD_ID)
                                .count(1)
                                .enchantLevel(3)
                                .augmentationId(0x0002_0001)
                                .elementals(List.of(ItemElemental.builder()
                                        .element(Elementals.FIRE)
                                        .value(60)
                                        .build()))
                                .arrived(1L)
                                .build()));
                return inventory.getItemByObjectId(registry.handleOf(itemId));
            });

            assertThat(actual).isNotNull();
            final Object[] state = WorldLoopExtension.callInPass(() -> new Object[] {
                actual.getEnchantLevel(),
                actual.getAugmentation().getAugmentationId(),
                actual.getElemental(Elementals.FIRE).getValue(),
                actual.hasRow()
            });
            assertThat(state).containsExactly(3, 0x0002_0001, 60, true);
            verifyNoInteractions(itemRows);
        }

        @Test
        void applyIncoming_shouldMergeAndMarkTheRowStored_whenTheTransactionInsertedTheStack() {
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                final Item arrows = inventory.addItem(ItemProcessType.NONE, ARROW_ID, 3, null, null);
                final IncomingTargets targets = targets(List.of(snapshot(ARROW_ID, 5)), 0);
                mailItems.applyIncoming(targets, List.of(committed(arrows, ARROW_ID, 8, ItemLocation.INVENTORY)));
                return new Object[] {arrows.getCount(), arrows.hasRow()};
            });

            assertThat(actual).containsExactly(8L, true);
        }

        @Test
        void applyIncoming_shouldTakeInAStackWithoutARow_whenItAppearedMeanwhile() {
            final UUID itemId = EntityIds.next();
            final Object[] actual = WorldLoopExtension.callInPass(() -> {
                final IncomingTargets targets = targets(List.of(snapshot(ARROW_ID, 5)), 0);
                inventory.addItem(ItemProcessType.NONE, ARROW_ID, 3, null, null);
                mailItems.applyIncoming(
                        targets,
                        List.of(MailItemRow.builder()
                                .itemId(itemId)
                                .ownerId(characterId)
                                .location(ItemLocation.INVENTORY)
                                .locationSlot(0)
                                .itemTemplateId(ARROW_ID)
                                .count(5)
                                .elementals(List.of())
                                .arrived(5L)
                                .build()));
                final Item arrows = inventory.getItemByItemId(ARROW_ID);
                return new Object[] {
                    inventory.getAllItemsByItemId(ARROW_ID).size(),
                    arrows.getObjectId() == registry.handleOf(itemId),
                    arrows.getCount()
                };
            });

            assertThat(actual).containsExactly(1, true, 8L);
        }

        @Test
        void applyIncoming_shouldMergeIntoTheLoadedWarehouseByTheArrivedCount() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = Item.restoreFromDb(handle, itemRow(ARROW_ID, 7, ItemLocation.WAREHOUSE));
                warehouse.addCommitted(stack);
                mailItems.applyIncoming(
                        noTargets(),
                        List.of(committed(stack, ARROW_ID, 9, ItemLocation.WAREHOUSE).toBuilder()
                                .arrived(4L)
                                .build()));
                return warehouse.getInventoryItemCount(ARROW_ID, -1);
            });

            assertThat(actual).isEqualTo(11);
        }

        @Test
        void applyIncoming_shouldDropTheAdena_whenThePaymentDeletedItsRow() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item adena = stored(Inventory.ADENA_ID, 300);
                final IncomingTargets targets = targets(List.of(), -300);
                mailItems.applyIncoming(
                        targets, List.of(committed(adena, Inventory.ADENA_ID, 0, ItemLocation.INVENTORY)));
                return inventory.getAdena() + inventory.getSize();
            });

            assertThat(actual).isZero();
        }
    }

    @Test
    void adenaLimit_shouldBeTheConfiguredMaximum() {
        assertThat(mailItems.adenaLimit()).isEqualTo(1_000_000);
    }

    record Refusal(List<AttachmentSnapshot> items, long adena, MailRefusal expected) {}

    private Reservation reserved(List<ItemCount> items, long fee) {
        return ((ReserveResult.Reserved<Reservation>) mailItems.reserve(handle, items, fee)).value();
    }

    private IncomingTargets noTargets() {
        return IncomingTargets.builder()
                .characterHandle(handle)
                .characterId(characterId)
                .stacks(List.of())
                .build();
    }

    private IncomingTargets targets(List<AttachmentSnapshot> items, long adena) {
        return ((ReserveResult.Reserved<IncomingTargets>)
                        mailItems.incomingTargets(handle, HolderType.INVENTORY, items, adena))
                .value();
    }

    private Item stored(int itemTemplateId, long count) {
        final Item item = Item.restoreFromDb(handle, itemRow(itemTemplateId, count, ItemLocation.INVENTORY));
        inventory.addCommitted(item);
        return item;
    }

    private ItemRow itemRow(int itemTemplateId, long count, ItemLocation location) {
        return ItemRow.builder()
                .objectId(registry.handleOf(EntityIds.next()))
                .itemId(itemTemplateId)
                .count(count)
                .location(location)
                .mana(-1)
                .build();
    }

    private MailItemRow committed(Item item, int itemTemplateId, long count, ItemLocation location) {
        return MailItemRow.builder()
                .itemId(registry.idOf(item.getObjectId()))
                .ownerId(characterId)
                .location(location)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .elementals(List.of())
                .build();
    }

    private ItemRowState rowOf(Item item, long count) {
        return WorldLoopExtension.callInPass(() -> ItemRowState.builder()
                .itemHandle(item.getObjectId())
                .itemId(registry.idOf(item.getObjectId()))
                .ownerId(characterId)
                .itemTemplateId(item.getId())
                .count(count)
                .build());
    }

    private static boolean[] pending(Item... items) {
        return WorldLoopExtension.callInPass(() -> {
            final boolean[] marks = new boolean[items.length];
            for (int i = 0; i < items.length; i++) {
                marks[i] = items[i].isPending();
            }
            return marks;
        });
    }

    private static AttachmentSnapshot snapshot(int itemTemplateId, long count) {
        return AttachmentSnapshot.builder()
                .itemTemplateId(itemTemplateId)
                .count(count)
                .enchantOptions(List.of())
                .build();
    }

    private static EtcItem template(int itemTemplateId, Map<String, Object> stats) {
        final Map<String, Object> all = new HashMap<>(stats);
        all.put("item_id", itemTemplateId);
        all.put("name", "Item " + itemTemplateId);
        return new EtcItem(new StatSet(all));
    }
}
