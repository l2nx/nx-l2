/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class QuestTest {
    private static final int RING_ID = 20_000_055;
    private static final String QUEST = "Q00999_Stored";

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem ring = new EtcItem(new StatSet(Map.of("item_id", RING_ID, "name", "Ring")));
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private MockedStatic<ItemData> itemData;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
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

        final Field field = Player.class.getDeclaredField("inventory");
        field.setAccessible(true);
        field.set(player, inventory);
        when(player.getInventory()).thenReturn(inventory);
        when(player.destroyItem(any(), any(Item.class), anyLong(), any(), anyBoolean()))
                .thenCallRealMethod();
        itemData = mockStatic(ItemData.class);
        itemData.when(ItemData::getInstance).thenReturn(mock(ItemData.class));
    }

    @AfterEach
    void tearDown() {
        itemData.close();
    }

    @Nested
    class TakeItems {
        @Test
        void takeItems_shouldTakeTheFreeItems_whenAnotherIsPending() {
            final Item[] pending = new Item[1];
            final boolean[] taken = new boolean[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                pending[0] = held();
                held();
                held();
                final IntList handle = IntList.of(pending[0].getObjectId());
                WorldPartition.pendingItems().reserve(handle);
                try {
                    taken[0] = Quest.takeItems(player, RING_ID, 2);
                } finally {
                    WorldPartition.pendingItems().release(handle);
                }
                return inventory.getItems();
            });

            assertThat(taken[0]).isTrue();
            assertThat(actual).containsExactly(pending[0]);
        }

        @Test
        void takeItems_shouldTakeNothing_whenOnlyAPendingItemCouldCompleteTheAmount() {
            final Item[] held = new Item[2];
            final boolean[] taken = new boolean[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                held[0] = held();
                held[1] = held();
                final IntList handle = IntList.of(held[0].getObjectId());
                WorldPartition.pendingItems().reserve(handle);
                try {
                    taken[0] = Quest.takeItems(player, RING_ID, 2);
                } finally {
                    WorldPartition.pendingItems().release(handle);
                }
                return inventory.getItems();
            });

            assertThat(taken[0]).isFalse();
            assertThat(actual).containsExactly(held);
        }

        @Test
        void takeItems_shouldTakeTheUnequippedInstance_whenAWornOneIsHeldToo() {
            final Item[] worn = new Item[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                worn[0] = worn(held());
                held();
                Quest.takeItems(player, RING_ID, 1);
                return inventory.getItems();
            });

            assertThat(actual).containsExactly(worn[0]);
        }

        @Test
        void takeItems_shouldTakeTheWornInstanceToo_whenTheUnequippedOnesFallShort() {
            final boolean[] taken = new boolean[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                worn(held());
                held();
                taken[0] = Quest.takeItems(player, RING_ID, 2);
                return inventory.getItems();
            });

            assertThat(taken[0]).isTrue();
            assertThat(actual).isEmpty();
        }
    }

    @Nested
    class TakeAllItems {
        @Test
        void takeAllItems_shouldTakeNothing_whenTheListNamesATemplateTwiceBeyondWhatIsHeld() {
            final Item[] held = new Item[3];
            final boolean[] taken = new boolean[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                for (int i = 0; i < held.length; i++) {
                    held[i] = held();
                }
                taken[0] = Quest.takeAllItems(player, new ItemHolder(RING_ID, 2), new ItemHolder(RING_ID, 2));
                return inventory.getItems();
            });

            assertThat(taken[0]).isFalse();
            assertThat(actual).containsExactly(held);
        }

        @Test
        void takeAllItems_shouldTakeEveryListedCount_whenTheItemsCoverThem() {
            final Item[] kept = new Item[1];
            final boolean[] taken = new boolean[1];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                held();
                held();
                kept[0] = held();
                taken[0] = Quest.takeAllItems(player, new ItemHolder(RING_ID, 1), new ItemHolder(RING_ID, 1));
                return inventory.getItems();
            });

            assertThat(taken[0]).isTrue();
            assertThat(actual).containsExactly(kept[0]);
        }
    }

    @Nested
    class PlayerEnter {
        @Test
        void playerEnter_shouldCreateTheStoredStatesAndVariables_withoutReading() {
            final Quest quest = mock(Quest.class);
            when(quest.getName()).thenReturn(QUEST);
            final AtomicReference<QuestState> created = new AtomicReference<>();
            doAnswer(call -> {
                        created.set(call.getArgument(0));
                        return null;
                    })
                    .when(player)
                    .setQuestState(any());
            when(quest.getQuestState(player, true)).thenAnswer(_ -> created.get());
            final ScriptManager scripts = mock(ScriptManager.class);
            when(scripts.getScript(QUEST)).thenReturn(quest);
            when(scripts.getScripts()).thenReturn(Map.of());
            final PlayerPersistenceManager persistence = mock(PlayerPersistenceManager.class);

            try (MockedStatic<ScriptManager> scriptManager = mockStatic(ScriptManager.class);
                    MockedStatic<PlayerPersistenceManager> persistenceManager =
                            mockStatic(PlayerPersistenceManager.class)) {
                scriptManager.when(ScriptManager::getInstance).thenReturn(scripts);
                persistenceManager.when(PlayerPersistenceManager::getInstance).thenReturn(persistence);
                WorldLoopExtension.runInPass(() -> Quest.playerEnter(
                        player,
                        List.of(new CharacterQuestVariable(QUEST, "<state>", "Started")),
                        List.of(new CharacterQuestVariable(QUEST, "cond", "2"))));
            }

            assertThat(created.get().isStarted()).isTrue();
            assertThat(created.get().get("cond")).isEqualTo("2");
            verifyNoInteractions(persistence);
        }
    }

    private static Item worn(Item item) {
        item.setItemLocation(ItemLocation.PAPERDOLL, Inventory.PAPERDOLL_RFINGER, false);
        return item;
    }

    private Item held() {
        final Item item = new Item(registry.allocateTransient(), ring);
        inventory.addItem(ItemProcessType.NONE, item, null, null);
        return item;
    }
}
