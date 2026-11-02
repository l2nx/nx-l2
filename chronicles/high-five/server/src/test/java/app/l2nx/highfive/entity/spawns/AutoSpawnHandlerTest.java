/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.spawns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.spawns.AutoSpawnHandler.AutoSpawnInstance;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class AutoSpawnHandlerTest {
    private static final int NPC_ID = 31_113;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    private static AutoSpawnHandler handler() {
        return WorldLoopExtension.callInPass(AutoSpawnHandler::new);
    }

    private static AutoSpawnInstance instance() {
        return new AutoSpawnInstance(NPC_ID, 0, 0, 0);
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("handlerCalls")
        void handlerCall_shouldThrow_whenCalledOffTheLoop(Consumer<AutoSpawnHandler> call) {
            final AutoSpawnHandler handler = handler();
            try {
                assertThatThrownBy(() -> call.accept(handler))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("touched from thread");
            } finally {
                WorldLoopExtension.runInPass(handler::unload);
            }
        }

        static Stream<Named<Consumer<AutoSpawnHandler>>> handlerCalls() {
            return Stream.of(
                    Named.of("size", AutoSpawnHandler::size),
                    Named.of("unload", AutoSpawnHandler::unload),
                    Named.of("registerSpawn", handler -> handler.registerSpawn(NPC_ID, -1, -1, -1)),
                    Named.of("removeSpawn(int)", handler -> handler.removeSpawn(1)),
                    Named.of("setAllActive", handler -> handler.setAllActive(false)),
                    Named.of("getAutoSpawnInstance", handler -> handler.getAutoSpawnInstance(NPC_ID, false)),
                    Named.of("getAutoSpawnInstances", handler -> handler.getAutoSpawnInstances(NPC_ID)),
                    Named.of("isSpawnRegistered", handler -> handler.isSpawnRegistered(1)),
                    Named.of("getInstance (static)", _ -> AutoSpawnHandler.getInstance()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("instanceCalls")
        void instanceCall_shouldThrow_whenCalledOffTheLoop(Consumer<AutoSpawnInstance> call) {
            final AutoSpawnInstance instance = instance();

            assertThatThrownBy(() -> call.accept(instance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AutoSpawnInstance>>> instanceCalls() {
            return Stream.of(
                    Named.of("getObjectId (accessor)", AutoSpawnInstance::getObjectId),
                    Named.of("getId (accessor)", AutoSpawnInstance::getId),
                    Named.of("setSpawnCount (accessor)", instance -> instance.setSpawnCount(2)),
                    Named.of("isSpawnActive (accessor)", AutoSpawnInstance::isSpawnActive),
                    Named.of("setRandomSpawn (accessor)", instance -> instance.setRandomSpawn(true)),
                    Named.of("setBroadcast", instance -> instance.setBroadcast(true)),
                    Named.of("getNpcList", AutoSpawnInstance::getNpcList),
                    Named.of("getSpawns", AutoSpawnInstance::getSpawns),
                    Named.of("addSpawnLocation", instance -> instance.addSpawnLocation(1, 2, 3, 0)),
                    Named.of("removeSpawnLocation", instance -> instance.removeSpawnLocation(0)));
        }
    }

    @Nested
    class RemoveSpawn {
        @Test
        void removeSpawn_shouldUnregisterTheInstanceByItsObjectId() {
            final AutoSpawnHandler handler = handler();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final AutoSpawnInstance registered = handler.registerSpawn(NPC_ID, -1, -1, -1);
                final boolean removed = handler.removeSpawn(registered);
                final List<Object> result = List.of(
                        removed,
                        handler.isSpawnRegistered(registered),
                        handler.isSpawnRegistered(registered.getObjectId()),
                        handler.getTimeToNextSpawn(registered));
                handler.unload();
                return result;
            });

            assertThat(actual).containsExactly(true, false, false, -1L);
        }

        @Test
        void unload_shouldUnregisterEverySpawn() {
            final AutoSpawnHandler handler = handler();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                final int loaded = handler.size();
                handler.unload();
                return List.of(loaded, handler.size());
            });

            assertThat(actual.get(0)).isPositive();
            assertThat(actual.get(1)).isZero();
        }
    }

    @Nested
    class SetSpawnActive {
        @Test
        void setSpawnActive_shouldKeepTheLiveTimer_whenTheSpawnIsActivatedAgain() {
            final AutoSpawnHandler handler = handler();
            final AutoSpawnInstance registered =
                    WorldLoopExtension.callInPass(() -> handler.registerSpawn(NPC_ID, 10_000, 60_000, 0));
            WorldLoopExtension.passAfter(Duration.ofSeconds(4));

            final long actual = WorldLoopExtension.callInPass(() -> {
                handler.setSpawnActive(registered, true);
                final long delay = handler.getTimeToNextSpawn(registered);
                handler.unload();
                return delay;
            });

            assertThat(actual).isBetween(5_000L, 6_000L);
        }

        @Test
        void setSpawnActive_shouldNotSpawnAgain_whenAOneShotSpawnAlreadyFired() {
            final AutoSpawnHandler handler = handler();
            final AutoSpawnInstance registered =
                    WorldLoopExtension.callInPass(() -> handler.registerSpawn(NPC_ID, 1_000, 60_000, 0));
            // The fired spawner finds no NPC data and logs; only its timer matters here.
            try (MockedStatic<NpcData> npcData = mockStatic(NpcData.class)) {
                WorldLoopExtension.passAfter(Duration.ofSeconds(2));
            }

            final long actual = WorldLoopExtension.callInPass(() -> {
                handler.setSpawnActive(registered, true);
                final long delay = handler.getTimeToNextSpawn(registered);
                handler.unload();
                return delay;
            });

            assertThat(actual).isNotPositive();
        }

        @Test
        void setSpawnActive_shouldScheduleAgain_whenTheSpawnWasDeactivated() {
            final AutoSpawnHandler handler = handler();
            final AutoSpawnInstance registered =
                    WorldLoopExtension.callInPass(() -> handler.registerSpawn(NPC_ID, 10_000, 60_000, 0));
            WorldLoopExtension.passAfter(Duration.ofSeconds(4));

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                handler.setSpawnActive(registered, false);
                final long inactive = handler.getTimeToNextSpawn(registered);
                handler.setSpawnActive(registered, true);
                final List<Object> result =
                        List.of(inactive, handler.getTimeToNextSpawn(registered), registered.isSpawnActive());
                handler.unload();
                return result;
            });

            assertThat(actual).containsExactly(0L, 10_000L, true);
        }
    }

    @Nested
    class GetAutoSpawnInstance {
        @Test
        void getAutoSpawnInstance_shouldAnswerTheFirstRegistered_whenLookedUpByNpcId() {
            final AutoSpawnHandler handler = handler();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                handler.unload();
                final AutoSpawnInstance first = handler.registerSpawn(NPC_ID, -1, -1, -1);
                final AutoSpawnInstance second = handler.registerSpawn(NPC_ID, -1, -1, -1);
                final List<Object> result = List.of(
                        handler.getAutoSpawnInstance(NPC_ID, false) == first,
                        handler.getAutoSpawnInstance(second.getObjectId(), true) == second,
                        handler.getAutoSpawnInstances(NPC_ID).equals(List.of(first, second)));
                handler.unload();
                return result;
            });

            assertThat(actual).containsExactly(true, true, true);
        }
    }

    @Nested
    class SpawnLocations {
        @Test
        void addSpawnLocation_shouldKeepTheInsertionOrder() {
            final AutoSpawnInstance instance = instance();

            final List<Location> actual = WorldLoopExtension.callInPass(() -> {
                instance.addSpawnLocation(1, 2, 3, 0);
                instance.addSpawnLocation(new int[] {4, 5, 6});
                return List.of(instance.removeSpawnLocation(0), instance.removeSpawnLocation(0));
            });

            assertThat(actual).extracting(Location::getX).containsExactly(1, 4);
        }

        @Test
        void addSpawnLocation_shouldRefuseAPoint_whenItIsNotThreeCoordinates() {
            final AutoSpawnInstance instance = instance();

            final boolean actual = WorldLoopExtension.callInPass(() -> instance.addSpawnLocation(new int[] {1, 2}));

            assertThat(actual).isFalse();
        }

        @Test
        void removeSpawnLocation_shouldBeNull_whenTheIndexIsOutOfRange() {
            final AutoSpawnInstance instance = instance();

            final List<Location> actual = WorldLoopExtension.callInPass(() -> {
                instance.addSpawnLocation(1, 2, 3, 0);
                return Arrays.asList(
                        instance.removeSpawnLocation(1),
                        instance.removeSpawnLocation(-1),
                        instance.removeSpawnLocation(0));
            });

            assertThat(actual).hasSize(3);
            assertThat(actual.subList(0, 2)).containsOnlyNulls();
            assertThat(actual.get(2).getX()).isEqualTo(1);
        }
    }
}
