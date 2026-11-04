/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.data.xml.HennaData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.FloodProtectors;
import app.l2nx.highfive.util.StatSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class RequestHennaEquipTest {
    private static final int SYMBOL_ID = 1;
    private static final int DYE_ID = 20_000_053;
    private static final int WEAR_COUNT = 10;
    private static final int WEAR_FEE = 100;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem dyes =
            new EtcItem(new StatSet(Map.of("item_id", DYE_ID, "name", "Dye", "is_stackable", true)));
    private final EtcItem adena =
            new EtcItem(new StatSet(Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true)));
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private final GameClient client = mock(GameClient.class);
    private final Henna henna = mock(Henna.class);
    private MockedStatic<HennaData> hennaData;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        new IdManager(registry);
        final Field field = Player.class.getDeclaredField("inventory");
        field.setAccessible(true);
        field.set(player, inventory);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getAdena()).thenCallRealMethod();
        when(player.getHennaEmptySlots()).thenReturn(1);
        when(player.isGM()).thenReturn(true);
        final FloodProtectors floodProtectors = mock(FloodProtectors.class);
        when(floodProtectors.canPerformTransaction()).thenReturn(true);
        when(client.getFloodProtectors()).thenReturn(floodProtectors);
        when(client.getPlayer()).thenReturn(player);

        when(henna.getDyeItemId()).thenReturn(DYE_ID);
        when(henna.getWearCount()).thenReturn(WEAR_COUNT);
        when(henna.getWearFee()).thenReturn(WEAR_FEE);
        when(henna.isAllowedClass(any())).thenReturn(true);
        final HennaData data = mock(HennaData.class);
        when(data.getHenna(SYMBOL_ID)).thenReturn(henna);
        hennaData = mockStatic(HennaData.class);
        hennaData.when(HennaData::getInstance).thenReturn(data);
    }

    @AfterEach
    void tearDown() {
        hennaData.close();
    }

    @Nested
    class Equip {
        @Test
        void equip_shouldTakeTheDyesAndTheFeeAndDrawTheSymbol_whenBothAreFree() {
            WorldLoopExtension.runInPass(() -> {
                held(dyes, WEAR_COUNT);
                held(adena, WEAR_FEE);
                equip();
            });

            verify(player)
                    .destroyItemByItemId(eq(ItemProcessType.FEE), eq(DYE_ID), eq((long) WEAR_COUNT), any(), eq(true));
            verify(player).reduceAdena(eq(ItemProcessType.FEE), eq((long) WEAR_FEE), any(), eq(false));
            verify(player).addHenna(henna);
        }

        @ParameterizedTest(name = "adena pending={0}")
        @ValueSource(booleans = {false, true})
        void equip_shouldTakeNothing_whenAMailInFlightReservedTheDyesOrTheAdena(boolean adenaPending) {
            WorldLoopExtension.runInPass(() -> {
                final Item dyeStack = held(dyes, WEAR_COUNT);
                final Item adenaStack = held(adena, WEAR_FEE);
                final IntList handle = IntList.of((adenaPending ? adenaStack : dyeStack).getObjectId());
                WorldPartition.pendingItems().reserve(handle);
                try {
                    equip();
                } finally {
                    WorldPartition.pendingItems().release(handle);
                }
            });

            verify(player, never()).destroyItemByItemId(any(), anyInt(), anyLong(), any(), anyBoolean());
            verify(player, never()).reduceAdena(any(), anyLong(), any(), anyBoolean());
            verify(player, never()).addHenna(any());
        }
    }

    private Item held(EtcItem template, long count) {
        final Item stack = new Item(registry.allocateTransient(), template);
        stack.setCount(count);
        inventory.addItem(ItemProcessType.NONE, stack, null, null);
        return stack;
    }

    private void equip() {
        final ByteBuf frame = Unpooled.buffer().writeIntLE(SYMBOL_ID);
        final RequestHennaEquip packet = new RequestHennaEquip();
        try {
            packet.decode(client, frame);
        } finally {
            frame.release();
        }
        packet.run();
    }
}
