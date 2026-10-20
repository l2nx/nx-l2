/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.CastleDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.model.CastleDoorUpgrade;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CastleDoorUpgradeRepositoryTest extends RepositoryTest {
    private static final AtomicInteger IDS = new AtomicInteger(5000);

    private final CastleDoorUpgradeRepository repository = new CastleDoorUpgradeRepository(JDBC);

    private static void insertUpgrade(int doorId, int castleId, int hpRatio) {
        JDBC.sql("INSERT INTO castle_door_upgrades (door_id, castle_id, hp_ratio) VALUES (?, ?, ?)")
                .param(doorId)
                .param(castleId)
                .param(hpRatio)
                .update();
    }

    @Nested
    class FindByCastle {
        @Test
        void findByCastle_shouldReturnUpgradesOfCastleOnly() {
            final int castleId = IDS.incrementAndGet();
            final int doorId = IDS.incrementAndGet();
            insertUpgrade(doorId, castleId, 2);
            insertUpgrade(IDS.incrementAndGet(), IDS.incrementAndGet(), 3);

            assertThat(repository.findByCastle(castleId))
                    .containsExactly(CastleDoorUpgrade.builder()
                            .doorId(doorId)
                            .hpRatio(2)
                            .build());
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldOverwriteRatio_whenDoorIsUpgraded() {
            final int castleId = IDS.incrementAndGet();
            final int doorId = IDS.incrementAndGet();
            insertUpgrade(doorId, castleId, 2);
            final CastleDoorUpgrade expected =
                    CastleDoorUpgrade.builder().doorId(doorId).hpRatio(5).build();

            repository.save(castleId, expected);

            assertThat(repository.findByCastle(castleId)).containsExactly(expected);
        }
    }

    @Nested
    class DeleteByCastle {
        @Test
        void deleteByCastle_shouldRemoveUpgradesOfCastleOnly() {
            final int castleId = IDS.incrementAndGet();
            final int otherCastleId = IDS.incrementAndGet();
            insertUpgrade(IDS.incrementAndGet(), castleId, 2);
            insertUpgrade(IDS.incrementAndGet(), otherCastleId, 2);

            repository.deleteByCastle(castleId);

            assertThat(repository.findByCastle(castleId)).isEmpty();
            assertThat(repository.findByCastle(otherCastleId)).hasSize(1);
        }
    }
}
