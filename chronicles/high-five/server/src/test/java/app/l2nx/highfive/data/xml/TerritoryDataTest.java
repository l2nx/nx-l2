/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.TerritoryTemplate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TerritoryDataTest {

    private static final TerritoryData DATA = TerritoryData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedTerritory() {
            assertThat(DATA.getTerritories()).hasSize(9);
        }

        @Test
        void load_shouldReadEveryImportedSpawn() {
            final int actual = DATA.getTerritories().stream()
                    .mapToInt(territory -> territory.spawns().size())
                    .sum();

            assertThat(actual).isEqualTo(254);
        }

        @Test
        void load_shouldReadTerritoryAttributes() {
            final TerritoryTemplate actual = DATA.getTerritory(81);

            assertThat(actual.castleId()).isEqualTo(1);
            assertThat(actual.fortId()).isEqualTo(101);
            assertThat(actual.spawns()).hasSize(22);
        }
    }
}
