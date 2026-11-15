/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.character.StoredCharacter;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.PlayerTemplateData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.templates.PlayerTemplate;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.AccountVariablesManager;
import app.l2nx.highfive.managers.CharacterRelationsManager;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.managers.PremiumManager;
import app.l2nx.highfive.managers.RebirthManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.olympiad.Hero;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterRecommendation;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.item.model.StoredItem;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PlayerLoadTest {
    private static final List<Class<?>> STORES = List.of(
            PlayerPersistenceManager.class,
            ItemManager.class,
            CharacterRelationsManager.class,
            AccountVariablesManager.class,
            RebirthManager.class,
            PremiumManager.class);
    private static final List<Class<?>> STATIC_DATA = List.of(
            GeoEngine.class,
            CharInfoTable.class,
            Hero.class,
            CursedWeaponsManager.class,
            InstanceManager.class,
            TerritoryWarManager.class);
    private static final List<String> WRITES = List.of("save", "delete", "update", "store");

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final Map<Class<?>, Object> stores = new LinkedHashMap<>();
    private final UUID characterId = EntityIds.next();
    private final UUID friendId = EntityIds.next();
    private final UUID adenaId = EntityIds.next();
    private final UUID accountId = EntityIds.next();

    @Nested
    class Load {
        @Test
        void load_shouldBuildThePlayerFromTheRows_withoutAReadOfItsOwn() {
            final List<Object> actual = inWorld(() -> {
                final Player player = Player.load(stored(StoredCharacter.WorldEntry.builder()
                        .questStates(List.of())
                        .questVariables(List.of())
                        .skillSaves(List.of())
                        .transmogItemIds(List.of())
                        .build()));
                return List.of(
                        player.getInventory().getAdena(), player.getFriendList(), player.getStoredWorldEntry() != null);
            });

            assertThat(actual)
                    .containsExactly(500L, Set.of(registry.findHandle(friendId).orElseThrow()), true);
            assertThat(reads()).as("reads of the build").isEmpty();
        }

        @Test
        void load_shouldTakeTheRestoredItemsOutOfTheWorldWithoutAWrite_whenARestoreStepFails() {
            final StoredCharacter broken =
                    stored(null).toBuilder().friends(null).build();

            final boolean actual = inWorld(() -> {
                assertThatThrownBy(() -> Player.load(broken)).isInstanceOf(NullPointerException.class);
                return World.findObject(registry.findHandle(adenaId).orElseThrow()) == null;
            });

            assertThat(actual).as("the adena left the world").isTrue();
            assertThat(writes())
                    .as("item writes of the failed build")
                    .noneMatch(call -> call.startsWith("ItemManager."));
        }

        @Test
        void load_shouldLeaveTheAccountVariablesOfTheAccount_whenARestoreStepFails() {
            final StoredCharacter broken =
                    stored(null).toBuilder().friends(null).build();

            final boolean actual = inWorld(() -> {
                assertThatThrownBy(() -> Player.load(broken)).isInstanceOf(NullPointerException.class);
                return WorldPartition.loadedGameAccounts().isLoaded(accountId);
            });

            assertThat(actual).isFalse();
        }

        @Test
        void load_shouldShareTheAccountVariables_betweenTwoCharactersOfOneAccount() {
            final UUID otherId = EntityIds.next();

            final List<Object> actual = inWorld(() -> {
                final Player trader = Player.load(stored(null));
                trader.setGamePoints(40);
                final Player playing = Player.load(stored(null).toBuilder()
                        .characterId(otherId)
                        .character(stored(null).character().toBuilder()
                                .objectId(registry.handleOf(otherId))
                                .name("Other")
                                .build())
                        .inventory(List.of())
                        .accountVariables(Map.of("PRIME_POINTS", "100"))
                        .build());
                return List.of(playing.getAccountVariables() == trader.getAccountVariables(), playing.getGamePoints());
            });

            assertThat(actual).containsExactly(true, 40L);
        }
    }

    @Nested
    class CheckRecoBonusTask {
        @Test
        void checkRecoBonusTask_shouldTakeTheStoredCounts_whenARowIsStored() {
            final List<Integer> actual = inWorld(() -> {
                final Player player = Player.load(stored(null));
                player.checkRecoBonusTask(CharacterRecommendation.builder()
                        .received(4)
                        .leftToGive(9)
                        .bonusTimeLeftMs(3_600_000)
                        .build());
                return List.of(player.getRecomHave(), player.getRecomLeft());
            });

            assertThat(actual).containsExactly(4, 9);
            assertThat(reads()).as("reads of the world entry").isEmpty();
        }

        @Test
        void checkRecoBonusTask_shouldStartTheHourglassForAnHour_whenNothingIsStored() {
            final int actual = inWorld(() -> {
                final Player player = Player.load(stored(null));
                player.checkRecoBonusTask(null);
                return player.getNevitHourglassTime();
            });

            assertThat(actual).isEqualTo(3_600);
            assertThat(reads()).as("reads of the world entry").isEmpty();
        }
    }

    @Nested
    class AddSkill {
        @Test
        void addSkill_shouldStoreNoRow_whenTheSameSkillIsAddedAgain() {
            final Skill skill = mock(Skill.class);
            when(skill.getId()).thenReturn(3);
            when(skill.getLevel()).thenReturn(1);

            inWorld(() -> {
                final Player player = Player.load(stored(null));
                clearInvocations(stores.get(PlayerPersistenceManager.class));
                player.addSkill(skill, true);
                player.addSkill(skill, true);
                return null;
            });

            final PlayerPersistenceManager persistence =
                    (PlayerPersistenceManager) stores.get(PlayerPersistenceManager.class);
            verify(persistence, times(1)).saveSkill(anyInt(), anyInt(), any());
            verify(persistence, never()).updateSkillLevel(anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    private StoredCharacter stored(StoredCharacter.WorldEntry worldEntry) {
        final CharacterRow character = CharacterRow.builder()
                .objectId(registry.handleOf(characterId))
                .accountId(accountId)
                .name("Loaded")
                .classId(PlayerClass.FIGHTER.getId())
                .baseClassId(PlayerClass.FIGHTER.getId())
                .level(1)
                .currentHp(100)
                .currentCp(50)
                .currentMp(30)
                .build();
        return StoredCharacter.builder()
                .characterId(characterId)
                .character(character)
                .subclasses(List.of())
                .classIndex(0)
                .otherCharacters(Map.of())
                .inventory(List.of(StoredItem.builder()
                        .id(adenaId)
                        .itemTemplateId(Inventory.ADENA_ID)
                        .count(500)
                        .location(ItemLocation.INVENTORY)
                        .mana(-1)
                        .build()))
                .warehouse(List.of())
                .freight(List.of())
                .skills(List.of())
                .macros(List.of())
                .shortcuts(List.of())
                .hennas(List.of())
                .bookmarks(List.of())
                .recipes(List.of())
                .recipeShop(List.of())
                .premiumItems(List.of())
                .itemReuses(List.of())
                .friends(List.of(friendId))
                .blocked(List.of())
                .contacts(List.of())
                .variables(Map.of())
                .accountVariables(Map.of())
                .rebirthSkillIds(List.of())
                .worldEntry(worldEntry)
                .build();
    }

    private <T> T inWorld(Supplier<T> body) {
        new IdManager(registry);
        final PlayerTemplate template = mock(PlayerTemplate.class);
        when(template.getPlayerClass()).thenReturn(PlayerClass.FIGHTER);
        final PlayerTemplateData templates = mock(PlayerTemplateData.class);
        when(templates.getTemplate(anyInt())).thenReturn(template);
        final ItemData items = mock(ItemData.class);
        when(items.getTemplate(Inventory.ADENA_ID))
                .thenReturn(new EtcItem(new StatSet(
                        Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true, "weight", 1))));
        final Map<Class<?>, Object> singletons = new LinkedHashMap<>();
        singletons.put(PlayerTemplateData.class, templates);
        singletons.put(ItemData.class, items);
        STATIC_DATA.forEach(type -> singletons.put(type, mock(type)));
        STORES.forEach(type -> stores.put(type, mock(type)));
        singletons.putAll(stores);

        final List<MockedStatic<?>> bound = new ArrayList<>();
        try {
            singletons.forEach((type, instance) -> bound.add(bind(type, instance)));
            return WorldLoopExtension.callInPass(body);
        } finally {
            bound.forEach(MockedStatic::close);
        }
    }

    // Static mocks are thread-local; the pass runs on the test thread.
    private static <T> MockedStatic<T> bind(Class<T> type, Object instance) {
        final MockedStatic<T> mocked = mockStatic(type);
        mocked.when(() -> type.getMethod("getInstance").invoke(null)).thenReturn(instance);
        return mocked;
    }

    private List<String> reads() {
        return calls(false);
    }

    private List<String> writes() {
        return calls(true);
    }

    private List<String> calls(boolean writes) {
        return stores.values().stream()
                .flatMap(store -> mockingDetails(store).getInvocations().stream())
                .map(Invocation::getMethod)
                .filter(method -> WRITES.stream().anyMatch(method.getName()::startsWith) == writes)
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .toList();
    }
}
