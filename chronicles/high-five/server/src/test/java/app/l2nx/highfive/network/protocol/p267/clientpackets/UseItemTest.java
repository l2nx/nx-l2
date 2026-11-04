/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.FloodProtectors;
import app.l2nx.highfive.util.StatSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
class UseItemTest {
    private static final int POTION_ID = 20_000_052;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem potions = new EtcItem(new StatSet(Map.of(
            "item_id",
            POTION_ID,
            "name",
            "Potion",
            "is_stackable",
            true,
            "handler",
            RecordingHandler.class.getSimpleName())));
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = new PlayerInventory(player);
    private final GameClient client = mock(GameClient.class);
    private final RecordingHandler handler = new RecordingHandler();

    @BeforeEach
    void setUp() {
        new IdManager(registry);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isGM()).thenReturn(true);
        final FloodProtectors floodProtectors = mock(FloodProtectors.class);
        when(floodProtectors.canUseItem()).thenReturn(true);
        when(client.getFloodProtectors()).thenReturn(floodProtectors);
        when(client.getPlayer()).thenReturn(player);
        ItemHandler.getInstance().registerHandler(handler);
    }

    @AfterEach
    void tearDown() {
        ItemHandler.getInstance().removeHandler(handler);
    }

    @Nested
    class Use {
        @ParameterizedTest(name = "pending={0}")
        @ValueSource(booleans = {false, true})
        void use_shouldReachTheItemHandler_onlyWhenTheItemIsFree(boolean pending) {
            final Item[] stack = new Item[1];
            WorldLoopExtension.runInPass(() -> {
                stack[0] = new Item(registry.allocateTransient(), potions);
                stack[0].setCount(5);
                inventory.addItem(ItemProcessType.NONE, stack[0], null, null);
                final IntList handle = IntList.of(stack[0].getObjectId());
                if (pending) {
                    WorldPartition.pendingItems().reserve(handle);
                }
                try {
                    use(stack[0]);
                } finally {
                    if (pending) {
                        WorldPartition.pendingItems().release(handle);
                    }
                }
            });

            assertThat(handler.used).isEqualTo(pending ? List.of() : List.of(stack[0]));
        }
    }

    private void use(Item item) {
        final ByteBuf frame = Unpooled.buffer().writeIntLE(item.getObjectId()).writeIntLE(0);
        final UseItem packet = new UseItem();
        try {
            packet.decode(client, frame);
        } finally {
            frame.release();
        }
        packet.run();
    }

    private static final class RecordingHandler implements IItemHandler {
        private final List<Item> used = new ArrayList<>();

        @Override
        public boolean onItemUse(Playable playable, Item item, boolean forceUse) {
            used.add(item);
            return true;
        }
    }
}
