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
class SpawnGroupEntryTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<SpawnGroupEntry> call) {
            final SpawnGroupEntry entry = new SpawnGroupEntry(2, 60, 10);

            assertThatThrownBy(() -> call.accept(entry))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<SpawnGroupEntry>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getTotal (accessor)", SpawnGroupEntry::getTotal),
                    Named.of("getRespawnDelay (accessor)", SpawnGroupEntry::getRespawnDelay),
                    Named.of("getRespawnRandom (accessor)", SpawnGroupEntry::getRespawnRandom),
                    Named.of("getAliveOrReserved (accessor)", SpawnGroupEntry::getAliveOrReserved),
                    Named.of("setAliveOrReserved (accessor)", entry -> entry.setAliveOrReserved(1)),
                    Named.of("addSlot", entry -> entry.addSlot(null)),
                    Named.of("getSlots", SpawnGroupEntry::getSlots),
                    Named.of("getSlotCount", SpawnGroupEntry::getSlotCount),
                    Named.of("getSlot", entry -> entry.getSlot(0)),
                    Named.of("hasSlot", entry -> entry.hasSlot(null)));
        }
    }

    @Nested
    class Slots {
        @Test
        void slotLookups_shouldAnswerInSlotOrder() {
            final SpawnGroupEntry entry = new SpawnGroupEntry(2, 0, 0);
            final Spawn first = SpawnTest.spawn();
            final Spawn second = SpawnTest.spawn();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                entry.addSlot(first);
                entry.addSlot(second);
                return List.of(entry.getSlotCount(), entry.getSlot(0), entry.getSlot(1));
            });

            assertThat(actual).containsExactly(2, first, second);
        }

        @Test
        void hasSlot_shouldCompareByIdentity_whenAnotherSpawnHasTheSameLocation() {
            final SpawnGroupEntry entry = new SpawnGroupEntry(1, 0, 0);
            final Spawn slot = SpawnTest.spawn();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                entry.addSlot(slot);
                return List.of(entry.hasSlot(slot), entry.hasSlot(SpawnTest.spawn()));
            });

            assertThat(actual).containsExactly(true, false);
        }

        @Test
        void getSlots_shouldReturnAReadOnlySnapshot() {
            final SpawnGroupEntry entry = new SpawnGroupEntry(1, 0, 0);
            final Spawn slot = SpawnTest.spawn();

            final List<Spawn> actual = WorldLoopExtension.callInPass(() -> {
                entry.addSlot(slot);
                final List<Spawn> snapshot = entry.getSlots();
                entry.addSlot(SpawnTest.spawn());
                return snapshot;
            });

            assertThat(actual).singleElement().isSameAs(slot);
            assertThatThrownBy(() -> actual.add(slot)).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
