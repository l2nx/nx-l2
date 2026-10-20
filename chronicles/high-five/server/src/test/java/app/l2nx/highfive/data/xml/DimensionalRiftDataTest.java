/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.RiftRoomTemplate;
import app.l2nx.highfive.data.holders.SpawnPoint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DimensionalRiftDataTest {

    private static final DimensionalRiftData DATA = DimensionalRiftData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedRoom() {
            assertThat(DATA.getRooms()).hasSize(56);
        }

        @Test
        void load_shouldKeepEverySpawn() {
            final int actual = DATA.getRooms().stream()
                    .mapToInt(room -> room.spawns().size())
                    .sum();

            assertThat(actual).isEqualTo(150);
        }

        @Test
        void load_shouldReadRoomBounds() {
            final RiftRoomTemplate actual = DATA.getRoom(1, 9);

            assertThat(actual.xMin()).isEqualTo(-110688);
            assertThat(actual.xMax()).isEqualTo(-109136);
            assertThat(actual.yMin()).isEqualTo(-181968);
            assertThat(actual.yMax()).isEqualTo(-181072);
            assertThat(actual.zMin()).isEqualTo(-6816);
            assertThat(actual.zMax()).isEqualTo(-6240);
            assertThat(actual.teleport())
                    .isEqualTo(
                            SpawnPoint.builder().x(-109912).y(-181520).z(-6752).build());
        }

        @ParameterizedTest(name = "area {0} room {1} boss={2}")
        @CsvSource({"1, 8, false", "1, 9, true", "6, 9, true", "0, 0, false"})
        void load_shouldReadBossFlag(int type, int roomId, boolean expected) {
            final boolean actual = DATA.getRoom(type, roomId).boss();

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void load_shouldLeaveTeleportNull_whenRoomIsRiftBounds() {
            final RiftRoomTemplate actual = DATA.getRoom(0, 1);

            assertThat(actual.teleport()).isNull();
            assertThat(actual.spawns()).isEmpty();
        }
    }
}
