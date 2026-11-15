/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerWarehouse;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.network.GameClient;
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
@ResourceLock("PlayerConfig")
@ResourceLock(WorldLoopExtension.LOCK)
class SendWareHouseDepositListTest {
    private static final int OWNER = 268_435_501;
    private static final int ARROW_ID = 20_000_051;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem arrows =
            new EtcItem(new StatSet(Map.of("item_id", ARROW_ID, "name", "Arrow", "is_stackable", true)));
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private final PlayerWarehouse warehouse = new PlayerWarehouse(player);
    private final GameClient client = mock(GameClient.class);
    private MockedStatic<ItemData> itemData;
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
        when(player.getActiveWarehouse()).thenReturn(warehouse);
        when(player.getWareHouseLimit()).thenReturn(100);
        when(player.isGM()).thenReturn(true);
        when(player.getActiveEnchantItemId()).thenReturn(Player.ID_NONE);
        when(player.getAdena()).thenReturn(1_000L);
        when(player.reduceAdena(any(), anyLong(), any(), anyBoolean())).thenReturn(true);
        when(player.checkItemManipulation(anyInt(), anyLong(), anyString())).thenCallRealMethod();
        final FloodProtectors floodProtectors = mock(FloodProtectors.class);
        when(floodProtectors.canPerformTransaction()).thenReturn(true);
        when(client.getFloodProtectors()).thenReturn(floodProtectors);
        when(client.getPlayer()).thenReturn(player);

        final ItemData data = mock(ItemData.class);
        when(data.getTemplate(ARROW_ID)).thenReturn(arrows);
        itemData = mockStatic(ItemData.class);
        itemData.when(ItemData::getInstance).thenReturn(data);

        maxItemsInPacket = PlayerConfig.MAX_ITEM_IN_PACKET;
        PlayerConfig.MAX_ITEM_IN_PACKET = 10;
    }

    @AfterEach
    void tearDown() {
        itemData.close();
        PlayerConfig.MAX_ITEM_IN_PACKET = maxItemsInPacket;
    }

    @Nested
    class Deposit {
        @Test
        void deposit_shouldTakeTheFeeAndMoveTheItem_whenTheItemIsFree() {
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = held(10);
                try {
                    deposit(stack, 4);
                    return new long[] {stack.getCount(), warehouse.getInventoryItemCount(ARROW_ID, -1)};
                } finally {
                    World.removeObject(stack);
                }
            });

            assertThat(actual).containsExactly(6, 4);
            verify(player).reduceAdena(eq(ItemProcessType.FEE), eq(30L), any(), eq(false));
        }

        @Test
        void deposit_shouldTakeNoFeeAndMoveNothing_whenAMailInFlightReservedTheItem() {
            final long[] actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = held(10);
                final IntList handle = IntList.of(stack.getObjectId());
                WorldPartition.pendingItems().reserve(handle);
                try {
                    deposit(stack, 4);
                    return new long[] {stack.getCount(), warehouse.getSize()};
                } finally {
                    WorldPartition.pendingItems().release(handle);
                    World.removeObject(stack);
                }
            });

            assertThat(actual).containsExactly(10, 0);
            verify(player, never()).reduceAdena(any(), anyLong(), any(), anyBoolean());
        }
    }

    private Item held(long count) {
        final Item stack = new Item(registry.allocateTransient(), arrows);
        stack.setCount(count);
        World.addObject(stack);
        inventory.addItem(ItemProcessType.NONE, stack, null, null);
        return stack;
    }

    private void deposit(Item stack, long count) {
        final ByteBuf frame =
                Unpooled.buffer().writeIntLE(1).writeIntLE(stack.getObjectId()).writeLongLE(count);
        final SendWareHouseDepositList packet = new SendWareHouseDepositList();
        try {
            packet.decode(client, frame);
        } finally {
            frame.release();
        }
        packet.run();
    }
}
