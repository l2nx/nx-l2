/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.AutoSpawnGroup;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutoSpawnDataTest {

    private static final AutoSpawnData DATA = AutoSpawnData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedGroup() {
            assertThat(DATA.getGroups()).hasSize(129);
        }

        @Test
        void load_shouldReadEveryImportedLocation() {
            final int actual = DATA.getGroups().stream()
                    .mapToInt(group -> group.locations().size())
                    .sum();

            assertThat(actual).isEqualTo(193);
        }

        @Test
        void load_shouldReadGroupAttributes() {
            final AutoSpawnGroup actual = DATA.getGroups().getFirst();

            assertThat(actual.npcId()).isEqualTo(31113);
            assertThat(actual.initialDelaySeconds()).isEqualTo(-1);
            assertThat(actual.broadcastSpawn()).isTrue();
            assertThat(actual.randomSpawn()).isTrue();
            assertThat(actual.locations()).hasSize(8);
        }

        @Test
        void load_shouldReadDelaysInSeconds_whenGroupDespawns() {
            final AutoSpawnGroup actual = DATA.getGroups().stream()
                    .filter(group -> group.npcId() == 32335)
                    .findFirst()
                    .orElseThrow();

            assertThat(actual.respawnDelaySeconds()).isEqualTo(120);
            assertThat(actual.despawnDelaySeconds()).isEqualTo(120);
        }
    }
}
