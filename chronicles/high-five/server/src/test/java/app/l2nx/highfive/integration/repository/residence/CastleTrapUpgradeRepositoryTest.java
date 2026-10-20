/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.CastleTrapUpgradeRepository;
import app.l2nx.highfive.repository.residence.model.CastleTrapUpgrade;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CastleTrapUpgradeRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CASTLE_IDS = new AtomicInteger(6000);

    private final CastleTrapUpgradeRepository repository = new CastleTrapUpgradeRepository(JDBC);

    private static void insertUpgrade(int castleId, int towerIndex, int trapLevel) {
        JDBC.sql("INSERT INTO castle_trap_upgrades (castle_id, tower_index, trap_level) VALUES (?, ?, ?)")
                .param(castleId)
                .param(towerIndex)
                .param(trapLevel)
                .update();
    }

    private static CastleTrapUpgrade upgrade(int towerIndex, int trapLevel) {
        return CastleTrapUpgrade.builder()
                .towerIndex(towerIndex)
                .trapLevel(trapLevel)
                .build();
    }

    @Nested
    class FindByCastle {
        @Test
        void findByCastle_shouldReturnUpgradesByTowerIndex() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertUpgrade(castleId, 1, 3);
            insertUpgrade(castleId, 0, 2);

            assertThat(repository.findByCastle(castleId)).containsExactly(upgrade(0, 2), upgrade(1, 3));
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldOverwriteLevel_whenTowerIsUpgraded() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertUpgrade(castleId, 0, 1);

            repository.save(castleId, upgrade(0, 4));

            assertThat(repository.findByCastle(castleId)).containsExactly(upgrade(0, 4));
        }
    }

    @Nested
    class DeleteByCastle {
        @Test
        void deleteByCastle_shouldRemoveUpgradesOfCastleOnly() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final int otherCastleId = CASTLE_IDS.incrementAndGet();
            insertUpgrade(castleId, 0, 1);
            insertUpgrade(otherCastleId, 0, 1);

            repository.deleteByCastle(castleId);

            assertThat(repository.findByCastle(castleId)).isEmpty();
            assertThat(repository.findByCastle(otherCastleId)).hasSize(1);
        }
    }
}
