/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ItemManagerTest {
    private static final int EVENT_ITEM = 20_000_001;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final ItemRepository items = mock(ItemRepository.class);
    private final ClanRepository clans = mock(ClanRepository.class);
    private final EtcItem eventTemplate =
            new EtcItem(new StatSet(Map.of("item_id", EVENT_ITEM, "name", "Event Token")));
    private final List<Item> spawned = new ArrayList<>();

    private ItemManager manager;

    @BeforeEach
    void createManager() {
        new IdManager(registry);
        manager = new ItemManager(
                items,
                mock(ItemAugmentationRepository.class),
                mock(ItemElementalRepository.class),
                mock(ItemVariableRepository.class),
                mock(TransactionTemplate.class));
    }

    @AfterEach
    void forgetItems() {
        WorldLoopExtension.runInPass(() -> spawned.forEach(World::removeObject));
    }

    @Nested
    class DestroyItemsOfTemplate {
        @Test
        void destroyItemsOfTemplate_shouldDestroyItemInMemory_whenClanWarehouseHoldsIt() {
            final ItemContainer warehouse = loadClanWarehouse();
            final Item token = item();
            WorldLoopExtension.runInPass(() -> warehouse.addItem(ItemProcessType.NONE, token, null, null));

            WorldLoopExtension.runInPass(() -> manager.destroyItemsOfTemplate(EVENT_ITEM));

            assertThat(warehouse.getItems()).doesNotContain(token);
            assertThat(token.getCount()).isZero();
            assertThat(token.getItemLocation()).isEqualTo(ItemLocation.VOID);
            assertThat(findObject(objectIdOf(token))).isNull();
            verify(items).deleteByItemIdExcept(EVENT_ITEM, List.of());
        }

        @Test
        void destroyItemsOfTemplate_shouldKeepRowOfLoadedItem_whenNoContainerHoldsIt() {
            final Item inTransit = item();

            WorldLoopExtension.runInPass(() -> manager.destroyItemsOfTemplate(EVENT_ITEM));

            final int inTransitId = objectIdOf(inTransit);
            assertThat(findObject(inTransitId)).isSameAs(inTransit);
            verify(items).deleteByItemIdExcept(EVENT_ITEM, List.of(inTransitId));
        }

        @Test
        void destroyItemsOfTemplate_shouldRemoveItemFromGround_whenItLiesThere() {
            final Item dropped = item();

            final boolean spawnedAfter = WorldLoopExtension.callInPass(() -> {
                dropped.setSpawned(true);
                manager.destroyItemsOfTemplate(EVENT_ITEM);
                return dropped.isSpawned();
            });

            assertThat(spawnedAfter).isFalse();
            assertThat(findObject(objectIdOf(dropped))).isNull();
            verify(items).deleteByItemIdExcept(EVENT_ITEM, List.of());
        }

        @Test
        void destroyItemsOfTemplate_shouldThrowAndKeepRows_whenCalledOffTheWorldLoop() {
            final Item token = item();

            assertThatThrownBy(() -> manager.destroyItemsOfTemplate(EVENT_ITEM))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");

            assertThat(findObject(objectIdOf(token))).isSameAs(token);
            verifyNoInteractions(items);
        }
    }

    private ItemContainer loadClanWarehouse() {
        final int clanId = registry.allocateTransient();
        when(clans.findAllClanIds()).thenReturn(List.of(clanId));
        final ClanTable table = new ClanTable(
                clans,
                mock(ClanNoticeRepository.class),
                mock(ClanRankPrivilegeRepository.class),
                mock(ClanSkillRepository.class),
                mock(ClanSubPledgeRepository.class),
                mock(ClanWarRepository.class),
                mock(CastleStateRepository.class),
                mock(CharacterRepository.class),
                mock(TransactionTemplate.class));
        table.load();
        return table.getClan(clanId).getWarehouse();
    }

    private static WorldObject findObject(int objectId) {
        return WorldLoopExtension.callInPass(() -> World.findObject(objectId));
    }

    private static int objectIdOf(WorldObject object) {
        return WorldLoopExtension.callInPass(object::getObjectId);
    }

    private Item item() {
        final Item item = WorldLoopExtension.callInPass(() -> {
            final Item created = new Item(registry.allocateTransient(), eventTemplate);
            World.addObject(created);
            return created;
        });
        spawned.add(item);
        return item;
    }
}
