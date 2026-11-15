/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.holders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.util.StatSet;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("PlayerConfig")
@ResourceLock(WorldLoopExtension.LOCK)
class TradeListTest {
    private static final int OWNER = 268_435_600;
    private static final int PLAYER = 268_435_601;
    private static final int SWORD_ID = 20_000_060;
    private static final long PRICE = 500;
    private static final int REMOVED = 3;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem sword = new EtcItem(new StatSet(Map.of("item_id", SWORD_ID, "name", "Sword")));
    private final EtcItem adena =
            new EtcItem(new StatSet(Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true)));
    private final Player owner = mock(Player.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory ownerInventory = new PlayerInventory(owner);
    private final PlayerInventory playerInventory = new PlayerInventory(player);
    private final List<Item> registered = new ArrayList<>();
    private MockedStatic<ItemData> itemData;
    private long maxAdena;

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
        field.set(owner, ownerInventory);
        field.set(player, playerInventory);
        for (Player trader : List.of(owner, player)) {
            when(trader.isOnline()).thenReturn(true);
            when(trader.isPlayer()).thenReturn(true);
            when(trader.asPlayer()).thenReturn(trader);
            when(trader.checkItemManipulation(anyInt(), anyLong(), anyString())).thenCallRealMethod();
            when(trader.getInventoryLimit()).thenReturn(80);
            when(trader.getName()).thenReturn("Trader");
        }
        when(owner.getObjectId()).thenReturn(OWNER);
        when(owner.getInventory()).thenReturn(ownerInventory);
        when(player.getObjectId()).thenReturn(PLAYER);
        when(player.getInventory()).thenReturn(playerInventory);

        final ItemData data = mock(ItemData.class);
        when(data.getTemplate(SWORD_ID)).thenReturn(sword);
        itemData = mockStatic(ItemData.class);
        itemData.when(ItemData::getInstance).thenReturn(data);

        maxAdena = PlayerConfig.MAX_ADENA;
        PlayerConfig.MAX_ADENA = 1_000_000;
    }

    @AfterEach
    void tearDown() {
        WorldLoopExtension.runInPass(() -> registered.forEach(World::removeObject));
        itemData.close();
        PlayerConfig.MAX_ADENA = maxAdena;
    }

    @Nested
    class PrivateStoreBuy {
        @Test
        void privateStoreBuy_shouldShowTheBuyerTheAdenaRemoved_whenThePriceSpendsAllOfIt() {
            final int[] coins = new int[1];
            final int result = WorldLoopExtension.callInPass(() -> {
                World.addObject(owner);
                final Item offered = held(ownerInventory, sword, 1);
                held(ownerInventory, adena, 1);
                coins[0] = held(playerInventory, adena, PRICE).getObjectId();
                final TradeList list = new TradeList(owner);
                list.addItem(offered.getObjectId(), 1, PRICE);
                try {
                    return list.privateStoreBuy(
                            player, Set.of(new RequestTrade(offered.getObjectId(), SWORD_ID, 1, PRICE)));
                } finally {
                    World.removeObject(owner);
                }
            });

            assertThat(result).isZero();
            assertThat(lastUpdateOf(player, coins[0]))
                    .extracting(ItemInfo::getChange)
                    .isEqualTo(REMOVED);
        }
    }

    @Nested
    class PrivateStoreSell {
        @Test
        void privateStoreSell_shouldShowTheStoreOwnerTheAdenaRemoved_whenThePriceSpendsAllOfIt() {
            final int[] coins = new int[1];
            final boolean sold = WorldLoopExtension.callInPass(() -> {
                final Item wanted = registered(new Item(registry.allocateTransient(), sword));
                coins[0] = held(ownerInventory, adena, PRICE).getObjectId();
                final Item offered = held(playerInventory, sword, 1);
                held(playerInventory, adena, 1);
                final TradeList list = new TradeList(owner);
                list.addItem(wanted.getObjectId(), 1, PRICE);
                return list.privateStoreSell(
                        player, new RequestTrade[] {new RequestTrade(offered.getObjectId(), SWORD_ID, 1, PRICE)});
            });

            assertThat(sold).isTrue();
            assertThat(lastUpdateOf(owner, coins[0]))
                    .extracting(ItemInfo::getChange)
                    .isEqualTo(REMOVED);
        }
    }

    private Item held(PlayerInventory inventory, EtcItem template, long count) {
        final Item item = registered(new Item(registry.allocateTransient(), template));
        item.setCount(count);
        inventory.addItem(ItemProcessType.NONE, item, null, null);
        return item;
    }

    private Item registered(Item item) {
        World.addObject(item);
        registered.add(item);
        return item;
    }

    private static ItemInfo lastUpdateOf(Player trader, int objectId) {
        final ArgumentCaptor<InventoryUpdate> updates = ArgumentCaptor.forClass(InventoryUpdate.class);
        verify(trader, atLeastOnce()).sendInventoryUpdate(updates.capture());
        return updates.getValue().getItemEntries().get(objectId);
    }
}
