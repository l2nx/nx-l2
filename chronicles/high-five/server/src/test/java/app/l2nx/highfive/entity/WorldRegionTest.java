/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static app.l2nx.highfive.entity.WorldObjectTest.objectWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.Fence;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
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

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class WorldRegionTest {

    private record Obstacles(
            Supplier<Object> factory,
            BiConsumer<WorldRegion, Object> add,
            Predicate<WorldRegion> has,
            BiPredicate<WorldRegion, Predicate<Object>> any) {}

    private static Obstacles doors() {
        return new Obstacles(
                () -> mock(Door.class),
                (region, door) -> region.addDoor((Door) door),
                WorldRegion::hasDoors,
                (region, test) -> region.anyDoor(test::test));
    }

    private static Obstacles fences() {
        return new Obstacles(
                () -> mock(Fence.class),
                (region, fence) -> region.addFence((Fence) fence),
                WorldRegion::hasFences,
                (region, test) -> region.anyFence(test::test));
    }

    @Nested
    class Ownership {
        static Stream<Arguments> offLoopCalls() {
            return Stream.of(
                    Arguments.of(Named.of("getVisibleObjects", (Consumer<WorldRegion>) WorldRegion::getVisibleObjects)),
                    Arguments.of(Named.of("hasDoors", (Consumer<WorldRegion>) WorldRegion::hasDoors)),
                    Arguments.of(Named.of("hasFences", (Consumer<WorldRegion>) WorldRegion::hasFences)),
                    Arguments.of(Named.of("anyDoor", (Consumer<WorldRegion>) region -> region.anyDoor(door -> true))),
                    Arguments.of(
                            Named.of("anyFence", (Consumer<WorldRegion>) region -> region.anyFence(fence -> true))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("offLoopCalls")
        void call_shouldThrow_whenCalledOffTheLoop(Consumer<WorldRegion> call) {
            final WorldRegion region = new WorldRegion(0, 0, 0);

            assertThatThrownBy(() -> call.accept(region)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void getVisibleObjects_shouldAnswer_whenCalledInPass() {
            final AtomicReference<Collection<WorldObject>> objects = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> objects.set(new WorldRegion(0, 0, 0).getVisibleObjects()));

            assertThat(objects.get()).isEmpty();
        }
    }

    @Nested
    class GetVisibleObjects {
        @Test
        void getVisibleObjects_shouldVisitEveryObject_whenTheLoopRemovesWhileIterating() {
            final List<WorldObject> expected = List.of(objectWithId(101), objectWithId(102), objectWithId(103));
            final List<WorldObject> visited = new ArrayList<>();
            final AtomicReference<Collection<WorldObject>> after = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(0, 0, 0);
                expected.forEach(region::addVisibleObject);
                for (WorldObject object : region.getVisibleObjects()) {
                    visited.add(object);
                    region.removeVisibleObject(object);
                }
                after.set(region.getVisibleObjects());
            });

            assertThat(visited).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(after.get()).isEmpty();
        }

        @Test
        void getVisibleObjects_shouldVisitOnlyTheSnapshot_whenTheLoopAddsWhileIterating() {
            final List<WorldObject> expected = List.of(objectWithId(111), objectWithId(112));
            final List<WorldObject> visited = new ArrayList<>();
            final AtomicReference<Collection<WorldObject>> after = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(0, 0, 0);
                expected.forEach(region::addVisibleObject);
                for (WorldObject object : region.getVisibleObjects()) {
                    visited.add(object);
                    region.addVisibleObject(objectWithId(object.getObjectId() + 100));
                }
                after.set(region.getVisibleObjects());
            });

            assertThat(visited).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(after.get()).hasSize(4);
        }
    }

    @Nested
    class Snapshots {
        @Test
        void getVisibleObjects_shouldBeReadOnly() {
            final AtomicReference<Throwable> thrown = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try {
                    new WorldRegion(0, 0, 0).getVisibleObjects().clear();
                } catch (RuntimeException e) {
                    thrown.set(e);
                }
            });

            assertThat(thrown.get()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class DoorAndFenceQueries {
        static Stream<Arguments> obstacles() {
            return Stream.of(Arguments.of(Named.of("door", doors())), Arguments.of(Named.of("fence", fences())));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("obstacles")
        void has_shouldFollowTheSet(Obstacles obstacles) {
            final AtomicBoolean before = new AtomicBoolean(true);
            final AtomicBoolean after = new AtomicBoolean();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(0, 0, 0);
                before.set(obstacles.has().test(region));
                obstacles.add().accept(region, obstacles.factory().get());
                after.set(obstacles.has().test(region));
            });

            assertThat(before.get()).isFalse();
            assertThat(after.get()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("obstacles")
        void any_shouldMatchOnlyPresentObstacles(Obstacles obstacles) {
            final Object present = obstacles.factory().get();
            final AtomicBoolean empty = new AtomicBoolean(true);
            final AtomicBoolean hit = new AtomicBoolean();
            final AtomicBoolean miss = new AtomicBoolean(true);

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(0, 0, 0);
                empty.set(obstacles.any().test(region, o -> true));
                obstacles.add().accept(region, present);
                hit.set(obstacles.any().test(region, o -> o == present));
                miss.set(obstacles.any().test(region, o -> o != present));
            });

            assertThat(empty.get()).isFalse();
            assertThat(hit.get()).isTrue();
            assertThat(miss.get()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("obstacles")
        void any_shouldTestEachPresentObstacleOnce_whenThePredicateAddsToTheSet(Obstacles obstacles) {
            final int present = 3;
            final AtomicInteger tested = new AtomicInteger();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(0, 0, 0);
                for (int i = 0; i < present; i++) {
                    obstacles.add().accept(region, obstacles.factory().get());
                }
                obstacles.any().test(region, o -> {
                    tested.incrementAndGet();
                    for (int i = 0; i < 64; i++) {
                        obstacles.add().accept(region, obstacles.factory().get());
                    }
                    return false;
                });
            });

            final int expected = present;
            assertThat(tested.get()).isEqualTo(expected);
        }
    }

    @Nested
    class IsSurroundingRegion {
        @ParameterizedTest(name = "({0},{1},{2}) -> {3}")
        @CsvSource({
            "4, 5, 6, true",
            "6, 6, 6, true",
            "4, 4, 4, true",
            "5, 5, 7, false",
            "7, 5, 5, false",
            "5, 3, 5, false"
        })
        void isSurroundingRegion_shouldMatchTheNeighbourGrid(int x, int y, int z, boolean expected) {
            final AtomicBoolean actual = new AtomicBoolean();

            WorldLoopExtension.runInPass(
                    () -> actual.set(new WorldRegion(5, 5, 5).isSurroundingRegion(new WorldRegion(x, y, z))));

            assertThat(actual.get()).isEqualTo(expected);
        }

        @Test
        void isSurroundingRegion_shouldBeTrue_whenTheRegionIsItself() {
            final AtomicBoolean actual = new AtomicBoolean();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(5, 5, 5);
                actual.set(region.isSurroundingRegion(region));
            });

            assertThat(actual.get()).isTrue();
        }

        @Test
        void isSurroundingRegion_shouldBeFalse_whenTheRegionIsNull() {
            final AtomicBoolean actual = new AtomicBoolean(true);

            WorldLoopExtension.runInPass(() -> actual.set(new WorldRegion(5, 5, 5).isSurroundingRegion(null)));

            assertThat(actual.get()).isFalse();
        }
    }

    @Nested
    class GetSurroundingRegions {
        @Test
        void getSurroundingRegions_shouldPutTheRegionFirst_whenTheGivenArrayHasItLater() {
            final WorldRegion region = new WorldRegion(5, 5, 5);
            final WorldRegion neighbour = new WorldRegion(4, 5, 5);
            final AtomicReference<List<WorldRegion>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                region.setSurroundingRegions(new WorldRegion[] {neighbour, region});
                actual.set(region.getSurroundingRegions());
            });

            assertThat(actual.get()).containsExactly(region, neighbour);
        }

        @Test
        void getSurroundingRegions_shouldIgnoreChanges_whenTheGivenArrayIsWrittenLater() {
            final WorldRegion region = new WorldRegion(5, 5, 5);
            final WorldRegion[] given = {region};
            final AtomicReference<List<WorldRegion>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                region.setSurroundingRegions(given);
                given[0] = new WorldRegion(4, 5, 5);
                actual.set(region.getSurroundingRegions());
            });

            assertThat(actual.get()).containsExactly(region);
            assertThat(region.surroundingRegionsArray()).containsExactly(region);
        }

        @Test
        void getSurroundingRegions_shouldBeReadOnly() {
            final AtomicReference<Throwable> thrown = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = new WorldRegion(5, 5, 5);
                region.setSurroundingRegions(new WorldRegion[] {region});
                try {
                    region.getSurroundingRegions().clear();
                } catch (RuntimeException e) {
                    thrown.set(e);
                }
            });

            assertThat(thrown.get()).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
