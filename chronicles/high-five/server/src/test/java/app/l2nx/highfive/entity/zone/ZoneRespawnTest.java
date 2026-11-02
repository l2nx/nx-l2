/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.zone.type.ResidenceTeleportZone;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import java.util.function.Function;
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
class ZoneRespawnTest {
    private static ZoneRespawn zone() {
        return WorldLoopExtension.callInPass(() -> {
            final ZoneRespawn zone = new ResidenceTeleportZone(70_601);
            zone.parseLoc(100, 200, 300, null);
            zone.parseLoc(400, 500, 600, "banish");
            return zone;
        });
    }

    @Nested
    class SpawnLocations {
        @ParameterizedTest(name = "{0}")
        @MethodSource("spawnGetters")
        void spawnGetter_shouldHandOutACopy_whenTheCallerMovesIt(Function<ZoneRespawn, Location> getter) {
            final ZoneRespawn zone = zone();

            final List<Location> actual = WorldLoopExtension.callInPass(() -> {
                final Location first = getter.apply(zone);
                final Location expected = first.clone();
                first.setXYZ(1, 2, 3);
                first.setInstanceId(77);
                return List.of(expected, getter.apply(zone));
            });

            assertThat(actual.get(1)).isEqualTo(actual.get(0));
        }

        static Stream<Named<Function<ZoneRespawn, Location>>> spawnGetters() {
            return Stream.of(
                    Named.of("getSpawnLoc", ZoneRespawn::getSpawnLoc),
                    Named.of("getOtherSpawnLoc", ZoneRespawn::getOtherSpawnLoc),
                    Named.of("getChaoticSpawnLoc", ZoneRespawn::getChaoticSpawnLoc),
                    Named.of("getBanishSpawnLoc", ZoneRespawn::getBanishSpawnLoc),
                    Named.of("getSpawns().get(0)", z -> z.getSpawns().get(0)));
        }

        @Test
        void getSpawns_shouldBeAReadOnlySnapshot() {
            final ZoneRespawn zone = zone();

            final List<Location> actual = WorldLoopExtension.callInPass(zone::getSpawns);

            assertThat(actual).extracting(Location::getX).containsExactly(100);
            assertThatThrownBy(() -> actual.add(new Location(1, 2, 3)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getBanishSpawnLoc_shouldAnswerTheBanishPoint() {
            final ZoneRespawn zone = zone();

            final Location actual = WorldLoopExtension.callInPass(zone::getBanishSpawnLoc);

            assertThat(List.of(actual.getX(), actual.getY(), actual.getZ())).containsExactly(400, 500, 600);
        }
    }
}
