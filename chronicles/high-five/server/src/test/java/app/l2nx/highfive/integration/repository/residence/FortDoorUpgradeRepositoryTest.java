/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.FortDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.model.FortDoorUpgrade;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FortDoorUpgradeRepositoryTest extends RepositoryTest {
    private static final AtomicInteger IDS = new AtomicInteger(10000);

    private final FortDoorUpgradeRepository repository = new FortDoorUpgradeRepository(JDBC);

    private static FortDoorUpgrade upgrade(int doorId, int hp) {
        return FortDoorUpgrade.builder().doorId(doorId).hp(hp).pDef(20).mDef(30).build();
    }

    private static void insertUpgrade(int doorId, int fortId) {
        JDBC.sql("INSERT INTO fort_door_upgrades (door_id, fort_id, hp, p_def, m_def) VALUES (?, ?, 100, 20, 30)")
                .param(doorId)
                .param(fortId)
                .update();
    }

    @Nested
    class FindByFort {
        @Test
        void findByFort_shouldReturnUpgradesOfFortOnly() {
            final int fortId = IDS.incrementAndGet();
            final int doorId = IDS.incrementAndGet();
            insertUpgrade(doorId, fortId);
            insertUpgrade(IDS.incrementAndGet(), IDS.incrementAndGet());

            assertThat(repository.findByFort(fortId)).containsExactly(upgrade(doorId, 100));
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldStoreUpgrade_whenDoorIsNew() {
            final int fortId = IDS.incrementAndGet();
            final FortDoorUpgrade expected = upgrade(IDS.incrementAndGet(), 150);

            repository.save(fortId, expected);

            assertThat(repository.findByFort(fortId)).containsExactly(expected);
        }

        @Test
        void save_shouldOverwriteUpgrade_whenDoorIsUpgraded() {
            final int fortId = IDS.incrementAndGet();
            final int doorId = IDS.incrementAndGet();
            insertUpgrade(doorId, fortId);

            repository.save(fortId, upgrade(doorId, 300));

            assertThat(repository.findByFort(fortId)).containsExactly(upgrade(doorId, 300));
        }
    }

    @Nested
    class DeleteByFort {
        @Test
        void deleteByFort_shouldRemoveUpgradesOfFortOnly() {
            final int fortId = IDS.incrementAndGet();
            final int otherFortId = IDS.incrementAndGet();
            insertUpgrade(IDS.incrementAndGet(), fortId);
            insertUpgrade(IDS.incrementAndGet(), otherFortId);

            repository.deleteByFort(fortId);

            assertThat(repository.findByFort(fortId)).isEmpty();
            assertThat(repository.findByFort(otherFortId)).hasSize(1);
        }
    }
}
