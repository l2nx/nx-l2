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
import app.l2nx.highfive.entity.itemcontainer.Inventory.PaperdollListener;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class InventoryTest {
    private static final int SWORD_ID = 20_000_020;
    private static final int ARROW_ID = 20_000_021;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem sword = new EtcItem(new StatSet(Map.of("item_id", SWORD_ID, "name", "Sword")));
    private final EtcItem arrow = new EtcItem(new StatSet(Map.of("item_id", ARROW_ID, "name", "Arrow")));

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
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Inventory> call) {
            final Doll doll = new Doll();

            assertThatThrownBy(() -> call.accept(doll))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Inventory>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getPaperdollItem", doll -> doll.getPaperdollItem(Inventory.PAPERDOLL_RHAND)),
                    Named.of("setPaperdollItem", doll -> doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, null)),
                    Named.of("getPaperdollItems", Inventory::getPaperdollItems),
                    Named.of("addPaperdollListener", doll -> doll.addPaperdollListener(new Recorder())),
                    Named.of("getTotalWeight", Inventory::getTotalWeight),
                    Named.of("getWearedMask", Inventory::getWearedMask),
                    Named.of("dropItem", doll -> doll.dropItem(ItemProcessType.NONE, 1, 1, null, null)),
                    Named.of("reloadEquippedItems", Inventory::reloadEquippedItems));
        }
    }

    @Nested
    class Listeners {
        @Test
        void setPaperdollItem_shouldEmptyTheOtherSlot_whenAListenerUnequipsIt() {
            final Doll doll = new Doll();
            final Item actual = WorldLoopExtension.callInPass(() -> {
                doll.addPaperdollListener(new AmmunitionDropper());
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, item(sword));
                doll.setPaperdollItem(Inventory.PAPERDOLL_LHAND, item(arrow));
                doll.unEquipItemInSlot(Inventory.PAPERDOLL_RHAND);
                return doll.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
            });

            assertThat(actual).isNull();
        }

        @Test
        void unEquipItemInSlotAndRecord_shouldRecordTheChainedUnequip_whenAListenerUnequipsAnotherSlot() {
            final Doll doll = new Doll();
            final Item[] expected = new Item[2];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                doll.addPaperdollListener(new AmmunitionDropper());
                expected[0] = item(sword);
                expected[1] = item(arrow);
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, expected[0]);
                doll.setPaperdollItem(Inventory.PAPERDOLL_LHAND, expected[1]);
                return doll.unEquipItemInSlotAndRecord(Inventory.PAPERDOLL_RHAND);
            });

            assertThat(actual).containsExactlyInAnyOrder(expected[0], expected[1]);
        }

        @Test
        void unEquipItemInSlotAndRecord_shouldReturnAReadOnlyList() {
            final Doll doll = new Doll();
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, item(sword));
                return doll.unEquipItemInSlotAndRecord(Inventory.PAPERDOLL_RHAND);
            });

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void setPaperdollItem_shouldNotifyAListenerFromTheNextChange_whenItIsAddedDuringANotify() {
            final Doll doll = new Doll();
            final Recorder late = new Recorder();
            final int[] actual = WorldLoopExtension.callInPass(() -> {
                doll.addPaperdollListener(new PaperdollListener() {
                    @Override
                    public void notifyEquiped(int slot, Item inst, Inventory inventory) {
                        inventory.addPaperdollListener(late);
                    }

                    @Override
                    public void notifyUnequiped(int slot, Item inst, Inventory inventory) {}
                });
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, item(sword));
                final int afterFirst = late.equipped.size();
                doll.setPaperdollItem(Inventory.PAPERDOLL_LHAND, item(arrow));
                return new int[] {afterFirst, late.equipped.size()};
            });

            assertThat(actual).containsExactly(0, 1);
        }

        @Test
        void reloadEquippedItems_shouldRenotifyEveryEquippedItem_whenNoListenerChangesThePaperdoll() {
            final Doll doll = new Doll();
            final Recorder recorder = new Recorder();
            final Item[] expected = new Item[2];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = item(sword);
                expected[1] = item(arrow);
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, expected[0]);
                doll.setPaperdollItem(Inventory.PAPERDOLL_LHAND, expected[1]);
                doll.addPaperdollListener(recorder);
                doll.reloadEquippedItems();
                return recorder.equipped;
            });

            assertThat(actual).containsExactly(expected[0], expected[1]);
        }

        @Test
        void reloadEquippedItems_shouldSkipALaterSlot_whenAListenerEmptiesIt() {
            final Doll doll = new Doll();
            final Recorder recorder = new Recorder();
            final Item[] expected = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = item(sword);
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, expected[0]);
                doll.setPaperdollItem(Inventory.PAPERDOLL_LHAND, item(arrow));
                doll.addPaperdollListener(new AmmunitionDropper());
                doll.addPaperdollListener(recorder);
                doll.reloadEquippedItems();
                return recorder.equipped;
            });

            assertThat(actual).containsExactly(expected[0]);
        }
    }

    @Nested
    class Paperdoll {
        @Test
        void getPaperdollItems_shouldReturnTheAcceptedItems_whenFiltersAreGiven() {
            final Doll doll = new Doll();
            final Item[] expected = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                expected[0] = item(arrow);
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, item(sword));
                doll.setPaperdollItem(Inventory.PAPERDOLL_LHAND, expected[0]);
                return doll.getPaperdollItems(item -> item.getId() == ARROW_ID);
            });

            assertThat(actual).containsExactly(expected[0]);
        }

        @Test
        void getPaperdollItems_shouldReturnAReadOnlyList() {
            final Doll doll = new Doll();
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                doll.setPaperdollItem(Inventory.PAPERDOLL_RHAND, item(sword));
                return doll.getPaperdollItems();
            });

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Drop {
        @Test
        void dropItem_shouldRemoveTheItem_whenTheInventoryHoldsIt() {
            final Doll doll = new Doll();
            final Item[] dropped = new Item[1];
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Item item = item(sword);
                doll.put(item);
                dropped[0] = doll.dropItem(ItemProcessType.NONE, item, null, null);
                return doll.getSize();
            });

            assertThat(dropped[0]).isNotNull();
            assertThat(actual).isZero();
        }

        @Test
        void dropItem_shouldReturnNull_whenTheInventoryDoesNotHoldTheItem() {
            final Doll doll = new Doll();
            final Item actual =
                    WorldLoopExtension.callInPass(() -> doll.dropItem(ItemProcessType.NONE, item(sword), null, null));

            assertThat(actual).isNull();
        }
    }

    private Item item(EtcItem template) {
        return new Item(registry.allocateTransient(), template);
    }

    private static final class Recorder implements PaperdollListener {
        private final List<Item> equipped = new ArrayList<>();

        @Override
        public void notifyEquiped(int slot, Item inst, Inventory inventory) {
            equipped.add(inst);
        }

        @Override
        public void notifyUnequiped(int slot, Item inst, Inventory inventory) {}
    }

    private static final class AmmunitionDropper implements PaperdollListener {
        @Override
        public void notifyEquiped(int slot, Item inst, Inventory inventory) {}

        @Override
        public void notifyUnequiped(int slot, Item inst, Inventory inventory) {
            if (slot == Inventory.PAPERDOLL_RHAND) {
                inventory.setPaperdollItem(Inventory.PAPERDOLL_LHAND, null);
            }
        }
    }

    private static final class Doll extends Inventory {
        private final Creature owner = mock(Creature.class);

        void put(Item item) {
            addItem(item);
        }

        @Override
        protected Creature getOwner() {
            return owner;
        }

        @Override
        protected ItemLocation getBaseLocation() {
            return ItemLocation.INVENTORY;
        }

        @Override
        protected ItemLocation getEquipLocation() {
            return ItemLocation.PAPERDOLL;
        }
    }
}
