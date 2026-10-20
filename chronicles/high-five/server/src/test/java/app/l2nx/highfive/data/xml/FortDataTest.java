/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.FortSpawn;
import app.l2nx.highfive.data.holders.FortTemplate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FortDataTest {

    private static final FortData DATA = FortData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedFort() {
            assertThat(DATA.getForts()).hasSize(21);
        }

        @Test
        void load_shouldReadEveryImportedSiegeGuard() {
            final int actual = DATA.getForts().stream()
                    .mapToInt(fort -> fort.siegeGuards().size())
                    .sum();

            assertThat(actual).isEqualTo(3707);
        }

        @Test
        void load_shouldReadEveryImportedSpawn() {
            final int actual = DATA.getForts().stream()
                    .mapToInt(fort -> fort.spawns().size())
                    .sum();

            assertThat(actual).isEqualTo(368);
        }

        @ParameterizedTest(name = "fort {0}: {1}, type {2}")
        @CsvSource({"101, Shanty, 0", "102, Southern, 1", "121, Monastic, 0"})
        void load_shouldReadFortAttributes(int fortId, String expectedName, int expectedType) {
            final FortTemplate actual = DATA.getFort(fortId);

            assertThat(actual.name()).isEqualTo(expectedName);
            assertThat(actual.type()).isEqualTo(expectedType);
        }

        @Test
        void load_shouldSetCastleId_onlyForSpecialEnvoys() {
            final List<FortSpawn> actual = DATA.getForts().stream()
                    .flatMap(fort -> fort.spawns().stream())
                    .toList();

            assertThat(actual)
                    .filteredOn(spawn -> spawn.type() == 3)
                    .hasSize(32)
                    .allSatisfy(spawn -> assertThat(spawn.castleId()).isNotNull());
            assertThat(actual)
                    .filteredOn(spawn -> spawn.type() != 3)
                    .allSatisfy(spawn -> assertThat(spawn.castleId()).isNull());
        }
    }
}
