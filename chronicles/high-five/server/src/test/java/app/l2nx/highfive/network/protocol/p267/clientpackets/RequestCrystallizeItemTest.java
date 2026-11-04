/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.CrystalType;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.FloodProtectors;
import app.l2nx.highfive.util.StatSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class RequestCrystallizeItemTest {
    private static final int GEM_ID = 20_000_054;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem gems = new EtcItem(new StatSet(Map.of(
            "item_id",
            GEM_ID,
            "name",
            "Gem",
            "is_stackable",
            true,
            "crystal_type",
            CrystalType.C,
            "crystal_count",
            5)));
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private final GameClient client = mock(GameClient.class);

    @BeforeEach
    void setUp() {
        new IdManager(registry);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getSkillLevel(anyInt())).thenReturn(1);
        final FloodProtectors floodProtectors = mock(FloodProtectors.class);
        when(floodProtectors.canPerformTransaction()).thenReturn(true);
        when(client.getFloodProtectors()).thenReturn(floodProtectors);
        when(client.getPlayer()).thenReturn(player);
    }

    @Nested
    class Crystallize {
        @ParameterizedTest(name = "pending={0}")
        @ValueSource(booleans = {false, true})
        void crystallize_shouldNotReachTheItem_whenAMailInFlightReservedIt(boolean pending) {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final Item stack = new Item(registry.allocateTransient(), gems);
                stack.setCount(10);
                inventory.addItem(ItemProcessType.NONE, stack, null, null);
                final IntList handle = IntList.of(stack.getObjectId());
                if (pending) {
                    WorldPartition.pendingItems().reserve(handle);
                }
                try {
                    crystallize(stack, 4);
                } finally {
                    if (pending) {
                        WorldPartition.pendingItems().release(handle);
                    }
                }
                return stack.getCount();
            });

            assertThat(actual).isEqualTo(10);
            verify(player, times(pending ? 0 : 1))
                    .sendPacket(
                            SystemMessageId
                                    .YOU_MAY_NOT_CRYSTALLIZE_THIS_ITEM_YOUR_CRYSTALLIZATION_SKILL_LEVEL_IS_TOO_LOW);
            verify(player, never()).setInCrystallize(anyBoolean());
        }
    }

    private void crystallize(Item item, long count) {
        final ByteBuf frame = Unpooled.buffer().writeIntLE(item.getObjectId()).writeLongLE(count);
        final RequestCrystallizeItem packet = new RequestCrystallizeItem();
        try {
            packet.decode(client, frame);
        } finally {
            frame.release();
        }
        packet.run();
    }
}
