/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.siege;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.siege.FortSiegeClanRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FortSiegeClanRepositoryTest extends RepositoryTest {
    private static final AtomicInteger FORT_IDS = new AtomicInteger(14000);

    private final FortSiegeClanRepository repository = new FortSiegeClanRepository(JDBC, registry);

    private static void insertSiegeClan(int fortId, UUID clanId) {
        JDBC.sql("INSERT INTO fort_siege_clans (fort_id, clan_id) VALUES (?, ?)")
                .param(fortId)
                .param(clanId)
                .update();
    }

    private static long count(int fortId) {
        return JDBC.sql("SELECT count(*) FROM fort_siege_clans WHERE fort_id = ?")
                .param(fortId)
                .query(Long.class)
                .single();
    }

    @Nested
    class FindClanIdsByFort {
        @Test
        void findClanIdsByFort_shouldReturnClanHandles() {
            final int fortId = FORT_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(fortId, clanId);
            insertSiegeClan(FORT_IDS.incrementAndGet(), insertClan());

            assertThat(repository.findClanIdsByFort(fortId)).containsExactly(registry.handleOf(clanId));
        }
    }

    @Nested
    class Exists {
        @Test
        void exists_shouldReturnTrue_whenClanIsRegistered() {
            final int fortId = FORT_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(fortId, clanId);

            assertThat(repository.exists(fortId, registry.handleOf(clanId))).isTrue();
        }

        @Test
        void exists_shouldReturnFalse_whenClanIsNotRegistered() {
            assertThat(repository.exists(FORT_IDS.incrementAndGet(), registry.handleOf(insertClan())))
                    .isFalse();
        }

        @Test
        void exists_shouldReturnFalse_whenHandleIsNoStoredEntity() {
            assertThat(repository.exists(FORT_IDS.incrementAndGet(), registry.allocateTransient()))
                    .isFalse();
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreClanUuid() {
            final int fortId = FORT_IDS.incrementAndGet();
            final UUID clanId = insertClan();

            repository.insert(fortId, registry.handleOf(clanId));

            assertThat(JDBC.sql("SELECT clan_id FROM fort_siege_clans WHERE fort_id = ?")
                            .param(fortId)
                            .query(UUID.class)
                            .single())
                    .isEqualTo(clanId);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenClan() {
            final int fortId = FORT_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(fortId, clanId);
            insertSiegeClan(fortId, insertClan());

            repository.delete(fortId, registry.handleOf(clanId));

            assertThat(count(fortId)).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByFort {
        @Test
        void deleteByFort_shouldRemoveRegistrationsOfFortOnly() {
            final int fortId = FORT_IDS.incrementAndGet();
            final int otherFortId = FORT_IDS.incrementAndGet();
            insertSiegeClan(fortId, insertClan());
            insertSiegeClan(otherFortId, insertClan());

            repository.deleteByFort(fortId);

            assertThat(count(fortId)).isZero();
            assertThat(count(otherFortId)).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByClan {
        @Test
        void deleteByClan_shouldRemoveClanFromEveryFort() {
            final int fortId = FORT_IDS.incrementAndGet();
            final int otherFortId = FORT_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(fortId, clanId);
            insertSiegeClan(otherFortId, clanId);
            insertSiegeClan(otherFortId, insertClan());

            repository.deleteByClan(registry.handleOf(clanId));

            assertThat(count(fortId)).isZero();
            assertThat(count(otherFortId)).isEqualTo(1);
        }
    }
}
