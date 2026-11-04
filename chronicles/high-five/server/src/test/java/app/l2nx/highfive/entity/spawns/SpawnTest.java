/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.spawns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.custom.RandomSpawnsConfig;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.type.NpcSpawnTerritory;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("RandomSpawnsConfig")
class SpawnTest {
    static final int NPC_ID = 20_545;

    static NpcTemplate template() {
        final NpcTemplate template =
                new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "type", "Monster", "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        return template;
    }

    static Spawn spawn() {
        try {
            return new Spawn(template());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Spawn> call) {
            final Spawn spawn = spawn();

            assertThatThrownBy(() -> call.accept(spawn))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Spawn>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId", Spawn::getId),
                    Named.of("getTemplate", Spawn::getTemplate),
                    Named.of("getAmount", Spawn::getAmount),
                    Named.of("setAmount", spawn -> spawn.setAmount(2)),
                    Named.of("setName", spawn -> spawn.setName("guard")),
                    Named.of("setRespawnDelay", spawn -> spawn.setRespawnDelay(60, 10)),
                    Named.of("getRespawnDelay", Spawn::getRespawnDelay),
                    Named.of("setChaseRange", spawn -> spawn.setChaseRange(500)),
                    Named.of("setSpawnTerritory", spawn -> spawn.setSpawnTerritory(null)),
                    Named.of("setGroup", spawn -> spawn.setGroup(null)),
                    Named.of("stopRespawn", Spawn::stopRespawn),
                    Named.of("isRespawnEnabled", Spawn::isRespawnEnabled),
                    Named.of("init", Spawn::init),
                    Named.of("doSpawn", Spawn::doSpawn),
                    Named.of("decreaseCount", spawn -> spawn.decreaseCount(null)),
                    Named.of("respawnNpc", spawn -> spawn.respawnNpc(null)),
                    Named.of("getLastSpawn", Spawn::getLastSpawn),
                    Named.of("getSpawnedNpcs", Spawn::getSpawnedNpcs),
                    Named.of("setXYZ(int, int, int) (Location)", spawn -> spawn.setXYZ(1, 2, 3)),
                    Named.of("setXYZ(ILocational) (Location)", spawn -> spawn.setXYZ(new Location(1, 2, 3))),
                    Named.of("setHeading (Location)", spawn -> spawn.setHeading(100)),
                    Named.of("isRandomHeading", Spawn::isRandomHeading),
                    Named.of("setInstanceId (Location)", spawn -> spawn.setInstanceId(7)),
                    Named.of("setLocation (Location)", spawn -> spawn.setLocation(new Location(1, 2, 3))),
                    Named.of("getX (Location)", spawn -> spawn.getX()),
                    Named.of("getY (Location)", spawn -> spawn.getY()),
                    Named.of("getZ (Location)", spawn -> spawn.getZ()),
                    Named.of("getHeading (Location)", spawn -> spawn.getHeading()),
                    Named.of("getInstanceId (Location)", spawn -> spawn.getInstanceId()),
                    Named.of("getLocation (Location)", spawn -> spawn.getLocation()),
                    Named.of("clone (Location)", spawn -> spawn.clone()),
                    Named.of("addSpawnListener (static)", _ -> Spawn.addSpawnListener(null)),
                    Named.of("removeSpawnListener (static)", _ -> Spawn.removeSpawnListener(null)));
        }

        @Test
        void constructor_shouldNotCheckTheOwner_whenALoaderBuildsTheSpawn() {
            final Spawn spawn = spawn();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> List.of(spawn.getX(), spawn.getZ()));

            assertThat(actual).containsExactly(0, -10_000);
        }
    }

    @Nested
    class LocationMutators {
        @Test
        void setters_shouldMoveTheSpawn_whenCalledOnTheLoop() {
            final Spawn spawn = spawn();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                spawn.setXYZ(83_400, 147_900, -3_400);
                spawn.setHeading(16_384);
                spawn.setInstanceId(5);
                return List.of(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getHeading(), spawn.getInstanceId());
            });

            assertThat(actual).containsExactly(83_400, 147_900, -3_400, 16_384, 5);
        }

        @Test
        void setLocation_shouldCopyCoordinatesHeadingAndInstance() {
            final Spawn spawn = spawn();
            final Location source = new Location(1, 2, 3, 4, 5);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                spawn.setLocation(source);
                return List.of(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getHeading(), spawn.getInstanceId());
            });

            assertThat(actual).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Nested
    class Heading {
        @ParameterizedTest(name = "{0} -> random {1}, heading {2}")
        @CsvSource({"-1, true, 0", "0, false, 0", "16384, false, 16384"})
        void setHeading_shouldMarkARandomHeading_whenGivenMinusOne(int heading, boolean random, int expected) {
            final Spawn spawn = spawn();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                spawn.setHeading(heading);
                return List.of(spawn.isRandomHeading(), spawn.getHeading());
            });

            assertThat(actual).containsExactly(random, expected);
        }

        @Test
        void setLocation_shouldClearTheRandomHeading() {
            final Spawn spawn = spawn();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                spawn.setHeading(-1);
                spawn.setLocation(new Location(1, 2, 3, 4));
                return spawn.isRandomHeading();
            });

            assertThat(actual).isFalse();
        }
    }

    @Nested
    class RandomMonsterSpawns {
        private static final int BASE_X = 83_400;
        private static final int BASE_Y = 147_900;
        private static final int BASE_Z = -3_400;

        private boolean savedEnabled;
        private int savedMin;
        private int savedMax;
        private Set<Integer> savedExcluded;

        @BeforeEach
        void enableRandomSpawns() {
            savedEnabled = RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS;
            savedMin = RandomSpawnsConfig.MOB_MIN_SPAWN_RANGE;
            savedMax = RandomSpawnsConfig.MOB_MAX_SPAWN_RANGE;
            savedExcluded = RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM;
            RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS = true;
            RandomSpawnsConfig.MOB_MIN_SPAWN_RANGE = -150;
            RandomSpawnsConfig.MOB_MAX_SPAWN_RANGE = 150;
            RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM = Set.of();
        }

        @AfterEach
        void restoreConfig() {
            RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS = savedEnabled;
            RandomSpawnsConfig.MOB_MIN_SPAWN_RANGE = savedMin;
            RandomSpawnsConfig.MOB_MAX_SPAWN_RANGE = savedMax;
            RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM = savedExcluded;
        }

        private static GeoEngine geo() {
            final GeoEngine geo = mock(GeoEngine.class);
            when(geo.canMoveToTarget(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(true);
            when(geo.canSeeTarget(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(true);
            when(geo.getHeight(anyInt(), anyInt(), anyInt())).thenAnswer(call -> call.getArgument(2));
            return geo;
        }

        private static void respawnTwice(GeoEngine geo, Spawn spawn, Npc npc, Runnable setup) {
            try (MockedStatic<GeoEngine> geoEngine = mockStatic(GeoEngine.class);
                    MockedStatic<ZoneManager> zoneManager = mockStatic(ZoneManager.class);
                    MockedStatic<WalkingManager> walking = mockStatic(WalkingManager.class)) {
                geoEngine.when(GeoEngine::getInstance).thenReturn(geo);
                zoneManager.when(ZoneManager::getInstance).thenReturn(mock(ZoneManager.class));
                walking.when(WalkingManager::getInstance).thenReturn(mock(WalkingManager.class));
                WorldLoopExtension.runInPass(() -> {
                    setup.run();
                    spawn.respawnNpc(npc);
                    spawn.respawnNpc(npc);
                });
            }
        }

        @Test
        void respawnNpc_shouldNotOffset_whenTheSpawnHasATerritory() {
            final GeoEngine geo = geo();
            final Npc npc = mock(Npc.class);
            when(npc.isMonster()).thenReturn(true);
            final NpcSpawnTerritory territory = mock(NpcSpawnTerritory.class);
            when(territory.getRandomPoint()).thenReturn(new Location(BASE_X, BASE_Y, BASE_Z));
            when(territory.getHighZ()).thenReturn(Integer.MAX_VALUE);
            final Spawn spawn = spawn();

            respawnTwice(geo, spawn, npc, () -> spawn.setSpawnTerritory(territory));

            verify(geo, never()).canMoveToTarget(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            verify(npc, times(2)).spawnMe(BASE_X, BASE_Y, BASE_Z);
        }

        @ParameterizedTest(name = "heading {0}")
        @ValueSource(ints = {-1, 500})
        void respawnNpc_shouldRollTheOffsetOnEveryRespawn_whateverTheHeading(int heading) {
            final GeoEngine geo = geo();
            final Npc npc = mock(Npc.class);
            when(npc.isMonster()).thenReturn(true);
            final Spawn spawn = spawn();

            respawnTwice(geo, spawn, npc, () -> {
                spawn.setXYZ(BASE_X, BASE_Y, BASE_Z);
                spawn.setHeading(heading);
            });
            final boolean randomHeading = WorldLoopExtension.callInPass(spawn::isRandomHeading);

            final ArgumentCaptor<Integer> x = ArgumentCaptor.forClass(Integer.class);
            final ArgumentCaptor<Integer> y = ArgumentCaptor.forClass(Integer.class);
            verify(geo, times(2)).canMoveToTarget(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            verify(npc, times(2)).spawnMe(x.capture(), y.capture(), eq(BASE_Z));
            assertThat(x.getAllValues()).allSatisfy(value -> assertThat(value).isBetween(BASE_X - 150, BASE_X + 150));
            assertThat(y.getAllValues()).allSatisfy(value -> assertThat(value).isBetween(BASE_Y - 150, BASE_Y + 150));
            assertThat(randomHeading).isEqualTo(heading == -1);
            if (heading != -1) {
                verify(npc, times(2)).setHeading(heading);
            }
        }
    }

    @Nested
    class SpawnLocation {
        @Test
        void getSpawnLocation_shouldBeNull_beforeTheFirstSpawn() {
            final Spawn spawn = spawn();

            final Location actual = WorldLoopExtension.callInPass(spawn::getSpawnLocation);

            assertThat(actual).isNull();
        }

        @Test
        void getSpawnLocation_shouldReturnACopy_whenTheCallerAdjustsIt() {
            final Spawn spawn = spawn();
            final Npc npc = mock(Npc.class);

            final Location actual;
            try (MockedStatic<GeoEngine> geoEngine = mockStatic(GeoEngine.class);
                    MockedStatic<ZoneManager> zoneManager = mockStatic(ZoneManager.class);
                    MockedStatic<WalkingManager> walking = mockStatic(WalkingManager.class)) {
                geoEngine.when(GeoEngine::getInstance).thenReturn(mock(GeoEngine.class));
                zoneManager.when(ZoneManager::getInstance).thenReturn(mock(ZoneManager.class));
                walking.when(WalkingManager::getInstance).thenReturn(mock(WalkingManager.class));
                actual = WorldLoopExtension.callInPass(() -> {
                    spawn.setXYZ(83_400, 147_900, -3_400);
                    spawn.respawnNpc(npc);
                    spawn.getSpawnLocation().setXYZ(0, 0, 0);
                    return spawn.getSpawnLocation();
                });
            }

            assertThat(actual).isEqualTo(new Location(83_400, 147_900, -3_400));
        }
    }

    @Nested
    class Equality {
        @Test
        void equals_shouldCompareByIdentity_whenTwoSpawnsShareALocation() {
            final Spawn first = spawn();
            final Spawn second = spawn();

            assertThat(first).isNotEqualTo(second).isEqualTo(first);
            assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
        }

        @Test
        void hashCode_shouldKeepTheSpawnInAHashSet_whenTheSpawnMoves() {
            final Spawn spawn = spawn();
            final Set<Spawn> spawns = new HashSet<>();
            spawns.add(spawn);

            WorldLoopExtension.runInPass(() -> spawn.setXYZ(83_400, 147_900, -3_400));

            assertThat(spawns.remove(spawn)).isTrue();
        }
    }

    @Nested
    class RespawnDelay {
        @Test
        void setRespawnDelay_shouldStoreBoundsInMilliseconds() {
            final Spawn spawn = spawn();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                spawn.setRespawnDelay(60, 10);
                return List.of(
                        spawn.getRespawnMinDelay(),
                        spawn.getRespawnMaxDelay(),
                        spawn.getRespawnDelay(),
                        spawn.hasRespawnRandom());
            });

            assertThat(actual).containsExactly(50_000, 70_000, 60_000, true);
        }
    }

    @Nested
    class SpawnedNpcs {
        @Test
        void getLastSpawn_shouldBeNull_whenNothingSpawned() {
            final Spawn spawn = spawn();

            assertThat(WorldLoopExtension.callInPass(spawn::getLastSpawn)).isNull();
        }

        @Test
        void getSpawnedNpcs_shouldBeEmptyAndReadOnly_whenNothingSpawned() {
            final Spawn spawn = spawn();

            final List<?> actual = WorldLoopExtension.callInPass(spawn::getSpawnedNpcs);

            assertThat(actual).isEmpty();
            assertThatThrownBy(() -> actual.add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class ToString {
        @Test
        void toString_shouldReadFieldsOnly_whenCalledOffTheLoop() {
            final Spawn spawn = spawn();

            final String actual = spawn.toString();

            assertThat(actual).isEqualTo("Spawn ID: 20545 X: 0 Y: 0 Z: -10000 Heading: 0");
        }
    }
}
