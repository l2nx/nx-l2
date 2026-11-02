/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.spawns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class SpawnGroupTest {
    private static SpawnGroup group(SpawnSelection selection, int maximumNpc) {
        return new SpawnGroup("pool", selection, maximumNpc);
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<SpawnGroup> call) {
            final SpawnGroup group = group(SpawnSelection.ALL, 0);

            assertThatThrownBy(() -> call.accept(group))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<SpawnGroup>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getName", SpawnGroup::getName),
                    Named.of("getSelection", SpawnGroup::getSelection),
                    Named.of("getMaximumNpc", SpawnGroup::getMaximumNpc),
                    Named.of("isActive", SpawnGroup::isActive),
                    Named.of("setActive", group -> group.setActive(false)),
                    Named.of("getEntries", SpawnGroup::getEntries),
                    Named.of("addEntry", group -> group.addEntry(new SpawnGroupEntry(1, 0, 0))),
                    Named.of("getAliveOrReserved (was synchronized)", SpawnGroup::getAliveOrReserved),
                    Named.of(
                            "tryReserve (was synchronized)",
                            group -> group.tryReserve(new SpawnGroupEntry(1, 0, 0), 1)),
                    Named.of("release (was synchronized)", group -> group.release(new SpawnGroupEntry(1, 0, 0), 1)),
                    Named.of("initialSpawn", SpawnGroup::initialSpawn),
                    Named.of("onUnitDeath (was a synchronized block)", group -> group.onUnitDeath(null)),
                    Named.of("getEntryBySlot", group -> group.getEntryBySlot(null)),
                    Named.of("spawnUnits", group -> group.spawnUnits(new SpawnGroupEntry(1, 0, 0), 1)));
        }
    }

    @Nested
    class TryReserve {
        @Test
        void tryReserve_shouldCountTheUnits_whenQuotaAndCapAllow() {
            final SpawnGroup group = group(SpawnSelection.RANDOM_FILL, 3);
            final SpawnGroupEntry entry = new SpawnGroupEntry(2, 0, 0);

            final List<Object> actual = WorldLoopExtension.callInPass(
                    () -> List.of(group.tryReserve(entry, 2), group.getAliveOrReserved(), entry.getAliveOrReserved()));

            assertThat(actual).containsExactly(true, 2, 2);
        }

        @Test
        void tryReserve_shouldRefuse_whenTheEntryQuotaWouldBeExceeded() {
            final SpawnGroup group = group(SpawnSelection.RANDOM_FILL, 0);
            final SpawnGroupEntry entry = new SpawnGroupEntry(1, 0, 0);

            final List<Object> actual = WorldLoopExtension.callInPass(
                    () -> List.of(group.tryReserve(entry, 1), group.tryReserve(entry, 1), group.getAliveOrReserved()));

            assertThat(actual).containsExactly(true, false, 1);
        }

        @Test
        void tryReserve_shouldRefuse_whenTheGroupCapWouldBeExceeded() {
            final SpawnGroup group = group(SpawnSelection.RANDOM_FILL, 1);
            final SpawnGroupEntry entry = new SpawnGroupEntry(5, 0, 0);

            final boolean actual = WorldLoopExtension.callInPass(() -> group.tryReserve(entry, 2));

            assertThat(actual).isFalse();
        }

        @Test
        void tryReserve_shouldIgnoreTheCap_whenSelectionIsRandom() {
            final SpawnGroup group = group(SpawnSelection.RANDOM, 1);
            final SpawnGroupEntry entry = new SpawnGroupEntry(5, 0, 0);

            final boolean actual = WorldLoopExtension.callInPass(() -> group.tryReserve(entry, 2));

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class Release {
        @Test
        void release_shouldReturnTheReservedUnits() {
            final SpawnGroup group = group(SpawnSelection.RANDOM_FILL, 0);
            final SpawnGroupEntry entry = new SpawnGroupEntry(3, 0, 0);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                group.tryReserve(entry, 3);
                group.release(entry, 2);
                return List.of(group.getAliveOrReserved(), entry.getAliveOrReserved());
            });

            assertThat(actual).containsExactly(1, 1);
        }
    }

    @Nested
    class GetEntryBySlot {
        @Test
        void getEntryBySlot_shouldFindTheEntryHoldingTheSlot() {
            final SpawnGroup group = group(SpawnSelection.ALL, 0);
            final SpawnGroupEntry first = new SpawnGroupEntry(1, 0, 0);
            final SpawnGroupEntry second = new SpawnGroupEntry(1, 0, 0);
            final Spawn slot = SpawnTest.spawn();

            final SpawnGroupEntry actual = WorldLoopExtension.callInPass(() -> {
                first.addSlot(SpawnTest.spawn());
                second.addSlot(slot);
                group.addEntry(first);
                group.addEntry(second);
                return group.getEntryBySlot(slot);
            });

            assertThat(actual).isSameAs(second);
        }

        @Test
        void getEntryBySlot_shouldBeNull_whenAnEqualSpawnIsNotTheSlot() {
            final SpawnGroup group = group(SpawnSelection.ALL, 0);
            final SpawnGroupEntry entry = new SpawnGroupEntry(1, 0, 0);

            final SpawnGroupEntry actual = WorldLoopExtension.callInPass(() -> {
                entry.addSlot(SpawnTest.spawn());
                group.addEntry(entry);
                return group.getEntryBySlot(SpawnTest.spawn());
            });

            assertThat(actual).isNull();
        }
    }

    @Nested
    class GetEntries {
        @Test
        void getEntries_shouldReturnAReadOnlySnapshotInEntryOrder() {
            final SpawnGroup group = group(SpawnSelection.ALL, 0);
            final SpawnGroupEntry first = new SpawnGroupEntry(1, 0, 0);
            final SpawnGroupEntry second = new SpawnGroupEntry(2, 0, 0);

            final List<SpawnGroupEntry> actual = WorldLoopExtension.callInPass(() -> {
                group.addEntry(first);
                group.addEntry(second);
                final List<SpawnGroupEntry> snapshot = group.getEntries();
                group.addEntry(new SpawnGroupEntry(3, 0, 0));
                return snapshot;
            });

            assertThat(actual).containsExactly(first, second);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class SpawnUnits {
        @Test
        void spawnUnits_shouldReleaseTheReservation_whenTheGroupIsInactive() {
            final SpawnGroup group = group(SpawnSelection.RANDOM_FILL, 0);
            final SpawnGroupEntry entry = new SpawnGroupEntry(2, 0, 0);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                group.tryReserve(entry, 2);
                group.setActive(false);
                return List.of(group.spawnUnits(entry, 2), group.getAliveOrReserved(), entry.getAliveOrReserved());
            });

            assertThat(actual).containsExactly(0, 0, 0);
        }
    }
}
