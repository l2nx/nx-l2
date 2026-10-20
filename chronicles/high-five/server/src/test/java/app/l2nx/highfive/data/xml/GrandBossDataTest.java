/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.GrandBossSpawn;
import app.l2nx.highfive.data.holders.SpawnPoint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GrandBossDataTest {

    private static final GrandBossData DATA = GrandBossData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedRow() {
            assertThat(DATA.getBosses()).hasSize(8);
        }

        @Test
        void load_shouldReadSpawnPoint() {
            final SpawnPoint expected =
                    SpawnPoint.builder().x(-21610).y(181594).z(-5734).heading(0).build();

            final GrandBossSpawn actual = DATA.getBoss(29001);

            assertThat(actual.spawnPoint()).isEqualTo(expected);
        }

        @Test
        void load_shouldLeaveSpawnPointNull_whenScriptPlacesBoss() {
            final GrandBossSpawn actual = DATA.getBoss(29118);

            assertThat(actual.spawnPoint()).isNull();
        }
    }
}
