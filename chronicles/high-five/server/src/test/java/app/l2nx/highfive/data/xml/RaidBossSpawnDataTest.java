/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.RaidBossSpawn;
import app.l2nx.highfive.data.holders.SpawnPoint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RaidBossSpawnDataTest {

    private static final RaidBossSpawnData DATA = RaidBossSpawnData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedRow() {
            assertThat(DATA.getSpawns()).hasSize(188);
        }

        @Test
        void load_shouldReadSpawnAttributes() {
            final RaidBossSpawn expected = RaidBossSpawn.builder()
                    .npcId(25001)
                    .spawnPoint(SpawnPoint.builder()
                            .x(-54416)
                            .y(146480)
                            .z(-2887)
                            .heading(0)
                            .build())
                    .respawnDelay(86400)
                    .respawnRandom(43200)
                    .build();

            final RaidBossSpawn actual = DATA.getSpawn(25001);

            assertThat(actual).isEqualTo(expected);
        }
    }
}
