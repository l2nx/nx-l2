/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static app.l2nx.highfive.entity.WorldObjectTest.objectWithId;
import static app.l2nx.highfive.entity.WorldTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class WorldTest {

    private record PlayerCounts(int base, int withBoth, int snapshotSize, int afterRemoval) {}

    private record FactionCounts(int good, int evil, int goodGap, int evilGap) {}

    private static WorldObject spawnedIn(WorldRegion region, int objectId) {
        final WorldObject object = objectWithId(objectId);
        object.setSpawned(true);
        object.setWorldRegion(region);
        region.addVisibleObject(object);
        return object;
    }

    private static WorldObject spawnedAt(WorldRegion region, int objectId, int x) {
        final WorldObject object = objectWithId(objectId);
        object.setXYZInvisible(x, 0, 0);
        object.setSpawned(true);
        object.setWorldRegion(region);
        region.addVisibleObject(object);
        return object;
    }

    private static WorldObject referenceAtOrigin(int objectId) {
        final WorldObject reference = objectWithId(objectId);
        reference.setXYZInvisible(0, 0, 0);
        return reference;
    }

    private static void inOneRegion(Consumer<WorldRegion> body) {
        WorldLoopExtension.runInPass(() -> {
            final WorldRegion region = new WorldRegion(0, 0, 0);
            region.setSurroundingRegions(new WorldRegion[] {region});
            withEveryObjectIn(region, () -> body.accept(region));
        });
    }

    private static void inTwoRegions(BiConsumer<WorldRegion, WorldRegion> body) {
        WorldLoopExtension.runInPass(() -> {
            final WorldRegion centre = new WorldRegion(0, 0, 0);
            final WorldRegion neighbour = new WorldRegion(1, 0, 0);
            centre.setSurroundingRegions(new WorldRegion[] {centre, neighbour});
            neighbour.setSurroundingRegions(new WorldRegion[] {neighbour, centre});
            withEveryObjectIn(centre, () -> body.accept(centre, neighbour));
        });
    }

    private static void withEveryObjectIn(WorldRegion region, Runnable body) {
        try (MockedStatic<World> ignored = mockStatic(World.class, invocation -> {
            final boolean regionOfObject = invocation.getMethod().getName().equals("getRegion")
                    && (invocation.getMethod().getParameterCount() == 1);
            return regionOfObject ? region : invocation.callRealMethod();
        })) {
            body.run();
        }
    }

    @Nested
    class Ownership {
        static Stream<Arguments> offLoopCalls() {
            return Stream.of(
                    Arguments.of(Named.of("getPlayers", (ThrowingCallable) World::getPlayers)),
                    Arguments.of(Named.of("findObject", (ThrowingCallable) () -> World.findObject(1))),
                    Arguments.of(Named.of("getPlayersCount", (ThrowingCallable) World::getPlayersCount)),
                    Arguments.of(Named.of("getGoodPlayersCount", (ThrowingCallable) World::getGoodPlayersCount)),
                    Arguments.of(Named.of("getEvilPlayersCount", (ThrowingCallable) World::getEvilPlayersCount)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("offLoopCalls")
        void call_shouldThrow_whenCalledOffTheLoop(ThrowingCallable call) {
            assertThatThrownBy(call).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void getPlayers_shouldAnswer_whenCalledInPass() {
            final AtomicReference<Collection<Player>> players = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> players.set(World.getPlayers()));

            assertThat(players.get()).isNotNull();
        }
    }

    @Nested
    class GetPlayersCount {
        @Test
        void getPlayersCount_shouldMatchThePlayerSet() {
            final Player first = player(0x7ffe_0321);
            final Player second = player(0x7ffe_0322);
            final AtomicReference<PlayerCounts> counts = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final int base = World.getPlayersCount();
                World.addObject(first);
                World.addObject(second);
                final int withBoth = World.getPlayersCount();
                final int snapshotSize = World.getPlayers().size();
                World.removeObject(first);
                World.removeObject(second);
                counts.set(new PlayerCounts(base, withBoth, snapshotSize, World.getPlayersCount()));
            });

            final PlayerCounts actual = counts.get();
            assertThat(actual.withBoth()).isEqualTo(actual.base() + 2);
            assertThat(actual.snapshotSize()).isEqualTo(actual.withBoth());
            assertThat(actual.afterRemoval()).isEqualTo(actual.base());
        }
    }

    @Nested
    class GetPlayers {
        @Test
        void getPlayers_shouldVisitEveryPlayer_whenTheLoopRemovesWhileIterating() {
            final List<Player> expected = List.of(player(0x7ffe_0301), player(0x7ffe_0302), player(0x7ffe_0303));
            final List<Player> visited = new ArrayList<>();
            final AtomicReference<Collection<Player>> after = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                expected.forEach(World::addObject);
                for (Player player : World.getPlayers()) {
                    if (expected.contains(player)) {
                        visited.add(player);
                        World.removeObject(player);
                    }
                }
                after.set(World.getPlayers());
            });

            assertThat(visited).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(after.get()).doesNotContainAnyElementsOf(expected);
        }

        @Test
        void getPlayers_shouldKeepItsElements_whenTheLoopAddsWhileIterating() {
            final Player present = player(0x7ffe_0311);
            final Player added = player(0x7ffe_0312);
            final AtomicReference<Collection<Player>> snapshot = new AtomicReference<>();
            final AtomicReference<Collection<Player>> after = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                World.addObject(present);
                snapshot.set(World.getPlayers());
                World.addObject(added);
                after.set(World.getPlayers());
                World.removeObject(present);
                World.removeObject(added);
            });

            assertThat(snapshot.get()).contains(present).doesNotContain(added);
            assertThat(after.get()).contains(present, added);
        }
    }

    @Nested
    class Snapshots {
        static Stream<Arguments> getters() {
            return Stream.of(
                    Arguments.of("getPlayers", (Supplier<Collection<?>>) World::getPlayers),
                    Arguments.of("getAllGoodPlayers", (Supplier<Collection<?>>) World::getAllGoodPlayers),
                    Arguments.of("getAllEvilPlayers", (Supplier<Collection<?>>) World::getAllEvilPlayers),
                    Arguments.of("getVisibleObjects", (Supplier<Collection<?>>) World::getVisibleObjects));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("getters")
        void getter_shouldBeReadOnly(String name, Supplier<Collection<?>> getter) {
            final AtomicReference<Throwable> thrown = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try {
                    getter.get().clear();
                } catch (RuntimeException e) {
                    thrown.set(e);
                }
            });

            assertThat(thrown.get()).as(name).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class FactionPlayersCount {
        @Test
        void counters_shouldMatchTheFactionSets() {
            final Player good = player(0x7ffe_0331);
            when(good.isGood()).thenReturn(true);
            final Player evil = player(0x7ffe_0332);
            when(evil.isEvil()).thenReturn(true);
            final AtomicReference<FactionCounts> counts = new AtomicReference<>();

            // Only the enabled faction system removes players from the faction sets; flipping it would race parallel
            // tests.
            WorldLoopExtension.runInPass(() -> {
                final int goodBase = World.getGoodPlayersCount();
                final int evilBase = World.getEvilPlayersCount();
                World.addFactionPlayerToWorld(good);
                World.addFactionPlayerToWorld(evil);
                counts.set(new FactionCounts(
                        World.getGoodPlayersCount() - goodBase,
                        World.getEvilPlayersCount() - evilBase,
                        World.getGoodPlayersCount() - World.getAllGoodPlayers().size(),
                        World.getEvilPlayersCount() - World.getAllEvilPlayers().size()));
            });

            final FactionCounts expected = new FactionCounts(1, 1, 0, 0);
            assertThat(counts.get()).isEqualTo(expected);
        }
    }

    @Nested
    class ForEachVisibleObject {
        @Test
        void forEachVisibleObject_shouldSkipAnObject_whenAnEarlierCallbackDeletedIt() {
            final List<WorldObject> visited = new ArrayList<>();

            inOneRegion(region -> {
                final WorldObject first = spawnedIn(region, 0x7ffe_0401);
                final WorldObject second = spawnedIn(region, 0x7ffe_0402);
                World.forEachVisibleObject(objectWithId(0x7ffe_0400), WorldObject.class, wo -> {
                    visited.add(wo);
                    ((wo == first) ? second : first).decayMe();
                });
            });

            assertThat(visited).hasSize(1);
        }

        @Test
        void forEachVisibleObject_shouldVisitEveryPair_whenTheCallbackScansAgain() {
            final List<List<WorldObject>> visited = new ArrayList<>();
            final List<List<WorldObject>> expected = new ArrayList<>();

            inOneRegion(region -> {
                final WorldObject first = spawnedIn(region, 0x7ffe_0411);
                final WorldObject second = spawnedIn(region, 0x7ffe_0412);
                for (WorldObject outer : List.of(first, second)) {
                    for (WorldObject inner : List.of(first, second)) {
                        if (inner != outer) {
                            expected.add(List.of(outer, inner));
                        }
                    }
                }
                World.forEachVisibleObject(
                        objectWithId(0x7ffe_0410),
                        WorldObject.class,
                        outer -> World.forEachVisibleObject(
                                outer, WorldObject.class, inner -> visited.add(List.of(outer, inner))));
            });

            assertThat(visited).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Nested
    class PredicateQueries {
        @Test
        void getFirstVisibleObject_shouldTestEveryCandidateOnce_whenThePredicateRemovesFromTheRegion() {
            final List<WorldObject> expected = new ArrayList<>();
            final List<WorldObject> tested = new ArrayList<>();

            inOneRegion(region -> {
                for (int i = 0; i < 64; i++) {
                    expected.add(spawnedIn(region, 0x7ffe_0500 + i));
                }
                World.getFirstVisibleObject(objectWithId(0x7ffe_04ff), WorldObject.class, wo -> {
                    tested.add(wo);
                    region.removeVisibleObject(wo);
                    return false;
                });
            });

            assertThat(tested).containsExactlyInAnyOrderElementsOf(expected);
        }

        @Test
        void getFirstVisibleObject_shouldStopAtTheFirstPass_whenAnEarlierRegionHoldsIt() {
            final List<WorldObject> tested = new ArrayList<>();
            final AtomicReference<WorldObject> expected = new AtomicReference<>();
            final AtomicReference<WorldObject> actual = new AtomicReference<>();

            inTwoRegions((centre, neighbour) -> {
                expected.set(spawnedIn(centre, 0x7ffe_0701));
                spawnedIn(neighbour, 0x7ffe_0702);
                actual.set(World.getFirstVisibleObject(objectWithId(0x7ffe_0700), WorldObject.class, wo -> {
                    tested.add(wo);
                    return true;
                }));
            });

            assertThat(actual.get()).isSameAs(expected.get());
            assertThat(tested).containsExactly(expected.get());
        }

        @Test
        void getNearestVisibleObject_shouldSkipTheNearer_whenThePredicateRejectsIt() {
            final AtomicReference<WorldObject> expected = new AtomicReference<>();
            final AtomicReference<WorldObject> actual = new AtomicReference<>();

            inTwoRegions((centre, neighbour) -> {
                final WorldObject nearer = spawnedAt(centre, 0x7ffe_0711, 100);
                expected.set(spawnedAt(neighbour, 0x7ffe_0712, 300));
                spawnedAt(neighbour, 0x7ffe_0713, 500);
                actual.set(World.getNearestVisibleObject(
                        referenceAtOrigin(0x7ffe_0710), WorldObject.class, wo -> wo != nearer));
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }

        @Test
        void getRandomVisibleObject_shouldPickTheOnlyPass_whenThePredicateRejectsTheRest() {
            final AtomicReference<WorldObject> expected = new AtomicReference<>();
            final AtomicReference<WorldObject> actual = new AtomicReference<>();

            inTwoRegions((centre, neighbour) -> {
                spawnedIn(centre, 0x7ffe_0721);
                expected.set(spawnedIn(neighbour, 0x7ffe_0722));
                spawnedIn(neighbour, 0x7ffe_0723);
                actual.set(World.getRandomVisibleObject(
                        objectWithId(0x7ffe_0720), WorldObject.class, wo -> wo == expected.get()));
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }

        @Test
        void getRandomVisibleObject_shouldReturnNull_whenNothingMatches() {
            final AtomicReference<WorldObject> actual = new AtomicReference<>(objectWithId(1));

            inOneRegion(region -> {
                spawnedIn(region, 0x7ffe_0601);
                actual.set(World.getRandomVisibleObject(objectWithId(0x7ffe_0600), Player.class));
            });

            assertThat(actual.get()).isNull();
        }
    }

    @Nested
    class RangeQueries {
        @ParameterizedTest(name = "range {0}")
        @CsvSource({"1000, true", "100, true", "99, false"})
        void getNearestVisibleObjectInRange_shouldPickTheNearestWithinRange(int range, boolean found) {
            final AtomicReference<WorldObject> nearest = new AtomicReference<>();
            final AtomicReference<WorldObject> actual = new AtomicReference<>();

            inTwoRegions((centre, neighbour) -> {
                nearest.set(spawnedAt(neighbour, 0x7ffe_0801, 100));
                spawnedAt(centre, 0x7ffe_0802, 300);
                actual.set(
                        World.getNearestVisibleObjectInRange(referenceAtOrigin(0x7ffe_0800), WorldObject.class, range));
            });

            assertThat(actual.get()).isEqualTo(found ? nearest.get() : null);
        }

        @ParameterizedTest(name = "range {0}")
        @CsvSource({"1000, 2", "300, 2", "299, 1", "50, 0"})
        void getVisibleObjectsInRange_shouldKeepOnlyObjectsWithinRange(int range, int expected) {
            final AtomicReference<List<WorldObject>> actual = new AtomicReference<>();

            inTwoRegions((centre, neighbour) -> {
                spawnedAt(centre, 0x7ffe_0811, 100);
                spawnedAt(neighbour, 0x7ffe_0812, 300);
                actual.set(World.getVisibleObjectsInRange(referenceAtOrigin(0x7ffe_0810), WorldObject.class, range));
            });

            assertThat(actual.get()).hasSize(expected);
        }
    }
}
