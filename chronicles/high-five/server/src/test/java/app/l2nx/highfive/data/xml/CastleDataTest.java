/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.CastleTemplate;
import app.l2nx.highfive.data.holders.SiegeGuardSpawn;
import app.l2nx.highfive.data.holders.SpawnPoint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CastleDataTest {

    private static final CastleData DATA = CastleData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedCastle() {
            assertThat(DATA.getCastles()).hasSize(9);
        }

        @Test
        void load_shouldReadEveryImportedSiegeGuard() {
            final int actual = DATA.getCastles().stream()
                    .mapToInt(castle -> castle.siegeGuards().size())
                    .sum();

            assertThat(actual).isEqualTo(3689);
        }

        @Test
        void load_shouldReadCastleAndGuardAttributes() {
            final SiegeGuardSpawn expected = SiegeGuardSpawn.builder()
                    .npcId(35085)
                    .spawnPoint(SpawnPoint.builder()
                            .x(-18460)
                            .y(113107)
                            .z(-2759)
                            .heading(15608)
                            .build())
                    .respawnDelay(10800)
                    .build();

            final CastleTemplate actual = DATA.getCastle(1);

            assertThat(actual.name()).isEqualTo("Gludio");
            assertThat(actual.siegeGuards().getFirst()).isEqualTo(expected);
        }
    }
}
