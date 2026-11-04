/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.BuyListData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.mechanics.buylist.BuyListHolder;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.util.FloodProtectors;
import app.l2nx.highfive.util.StatSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
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
class RequestSellItemTest {
    private static final int OWNER = 268_435_500;
    private static final int LIST_ID = 1;
    private static final int ARROW_ID = 20_000_050;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem arrows =
            new EtcItem(new StatSet(Map.of("item_id", ARROW_ID, "name", "Arrow", "is_stackable", true, "price", 100)));
    private final EtcItem adena =
            new EtcItem(new StatSet(Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true)));
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private final GameClient client = mock(GameClient.class);
    private MockedStatic<BuyListData> buyLists;
    private boolean allowRefund;
    private long maxAdena;
    private int maxItemsInPacket;

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
        when(player.getObjectId()).thenReturn(OWNER);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isGM()).thenReturn(true);
        when(player.checkItemManipulation(anyInt(), anyLong(), anyString())).thenCallRealMethod();
        final FloodProtectors floodProtectors = mock(FloodProtectors.class);
        when(floodProtectors.canPerformTransaction()).thenReturn(true);
        when(client.getFloodProtectors()).thenReturn(floodProtectors);
        when(client.getPlayer()).thenReturn(player);

        final BuyListData data = mock(BuyListData.class);
        when(data.getBuyList(LIST_ID)).thenReturn(mock(BuyListHolder.class));
        buyLists = mockStatic(BuyListData.class);
        buyLists.when(BuyListData::getInstance).thenReturn(data);

        allowRefund = GeneralConfig.ALLOW_REFUND;
        maxAdena = PlayerConfig.MAX_ADENA;
        maxItemsInPacket = PlayerConfig.MAX_ITEM_IN_PACKET;
        GeneralConfig.ALLOW_REFUND = false;
        PlayerConfig.MAX_ADENA = 1_000_000;
        PlayerConfig.MAX_ITEM_IN_PACKET = 10;
    }

    @AfterEach
    void tearDown() {
        buyLists.close();
        GeneralConfig.ALLOW_REFUND = allowRefund;
        PlayerConfig.MAX_ADENA = maxAdena;
        PlayerConfig.MAX_ITEM_IN_PACKET = maxItemsInPacket;
    }

    @Nested
    class Sell {
        @Test
        void sell_shouldPayThePriceAndTakeTheItem_whenTheItemIsFree() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = held(10);
                try {
                    sell(stack, 4);
                    return stack.getCount();
                } finally {
                    World.removeObject(stack);
                }
            });

            assertThat(actual).isEqualTo(6);
            verify(player).addAdena(eq(ItemProcessType.SELL), eq(200L), any(), eq(false));
        }

        @Test
        void sell_shouldKeepTheItem_whenThePriceWouldPassTheAdenaLimitOnTopOfTheHeldAdena() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item coins = new Item(registry.allocateTransient(), adena);
                coins.setCount(999_900);
                World.addObject(coins);
                inventory.addItem(ItemProcessType.NONE, coins, null, null);
                final Item stack = held(10);
                try {
                    sell(stack, 4);
                    return stack.getCount();
                } finally {
                    World.removeObject(stack);
                    World.removeObject(coins);
                }
            });

            assertThat(actual).isEqualTo(10);
            verify(player).sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
            verify(player).addAdena(eq(ItemProcessType.SELL), eq(0L), any(), eq(false));
        }

        @Test
        void sell_shouldPayNothingAndKeepTheItem_whenAMailInFlightReservedIt() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = held(10);
                final IntList handle = IntList.of(stack.getObjectId());
                WorldPartition.pendingItems().reserve(handle);
                try {
                    sell(stack, 4);
                    return stack.getCount();
                } finally {
                    WorldPartition.pendingItems().release(handle);
                    World.removeObject(stack);
                }
            });

            assertThat(actual).isEqualTo(10);
            verify(player).addAdena(eq(ItemProcessType.SELL), eq(0L), any(), eq(false));
        }
    }

    private Item held(long count) {
        final Item stack = new Item(registry.allocateTransient(), arrows);
        stack.setCount(count);
        World.addObject(stack);
        inventory.addItem(ItemProcessType.NONE, stack, null, null);
        return stack;
    }

    private void sell(Item stack, long count) {
        final ByteBuf frame = Unpooled.buffer()
                .writeIntLE(LIST_ID)
                .writeIntLE(1)
                .writeIntLE(stack.getObjectId())
                .writeIntLE(ARROW_ID)
                .writeLongLE(count);
        final RequestSellItem packet = new RequestSellItem();
        try {
            packet.decode(client, frame);
        } finally {
            frame.release();
        }
        packet.run();
    }
}
