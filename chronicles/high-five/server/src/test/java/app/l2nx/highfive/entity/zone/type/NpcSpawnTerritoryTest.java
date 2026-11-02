/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.entity.zone.form.ZoneCuboid;
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
class NpcSpawnTerritoryTest {
    private static NpcSpawnTerritory territory() {
        return new NpcSpawnTerritory("territory", new ZoneCuboid(0, 1000, 0, 1000, -100, 100));
    }

    private static ZoneCuboid westernHalf() {
        return new ZoneCuboid(0, 500, 0, 1000, -100, 100);
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<NpcSpawnTerritory> call) {
            final NpcSpawnTerritory territory = territory();

            assertThatThrownBy(() -> call.accept(territory))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<NpcSpawnTerritory>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getName (accessor)", NpcSpawnTerritory::getName),
                    Named.of("addBannedTerritory", t -> t.addBannedTerritory(westernHalf())),
                    Named.of("clearBannedTerritories", NpcSpawnTerritory::clearBannedTerritories),
                    Named.of("getRandomPoint", NpcSpawnTerritory::getRandomPoint),
                    Named.of("isInsideZone(x, y, z)", t -> t.isInsideZone(1, 1, 0)),
                    Named.of("isInsideZone(x, y)", t -> t.isInsideZone(1, 1)),
                    Named.of("getHighZ (accessor)", NpcSpawnTerritory::getHighZ),
                    Named.of("visualizeZone", t -> t.visualizeZone(0)));
        }
    }

    @Nested
    class BannedTerritories {
        @Test
        void isInsideZone_shouldExcludeABannedPart() {
            final NpcSpawnTerritory territory = territory();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                territory.addBannedTerritory(westernHalf());
                return List.of(territory.isInsideZone(250, 500), territory.isInsideZone(750, 500));
            });

            assertThat(actual).containsExactly(false, true);
        }

        @Test
        void clearBannedTerritories_shouldOpenTheBannedPartAgain() {
            final NpcSpawnTerritory territory = territory();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                territory.addBannedTerritory(westernHalf());
                territory.clearBannedTerritories();
                return territory.isInsideZone(250, 500);
            });

            assertThat(actual).isTrue();
        }

        @Test
        void addBannedTerritory_shouldBanTheSameParts_whenTheSpawnDataLoadsAgain() {
            final NpcSpawnTerritory territory = territory();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                territory.addBannedTerritory(westernHalf());
                territory.clearBannedTerritories();
                territory.addBannedTerritory(westernHalf());
                return List.of(territory.isInsideZone(250, 500), territory.isInsideZone(750, 500));
            });

            assertThat(actual).containsExactly(false, true);
        }
    }
}
