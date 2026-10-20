/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.FortStateRepository;
import app.l2nx.highfive.repository.residence.model.FortState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FortStateRepositoryTest extends RepositoryTest {
    private static final AtomicInteger FORT_IDS = new AtomicInteger(9000);

    private final FortStateRepository repository = new FortStateRepository(JDBC, registry);

    private static Map<String, Object> row(int fortId) {
        return JDBC.sql("SELECT * FROM fort_states WHERE fort_id = ?")
                .param(fortId)
                .query()
                .singleRow();
    }

    @Nested
    class FindById {
        @Test
        void findById_shouldReturnEmpty_whenNoStateRow() {
            assertThat(repository.findById(FORT_IDS.incrementAndGet())).isEmpty();
        }

        @Test
        void findById_shouldReturnZeroSentinels_whenColumnsAreNull() {
            final int fortId = FORT_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO fort_states (fort_id) VALUES (?)")
                    .param(fortId)
                    .update();

            assertThat(repository.findById(fortId))
                    .contains(FortState.builder().fortId(fortId).build());
        }

        @Test
        void findById_shouldReturnOwnerHandleAndTimes_whenSet() {
            final int fortId = FORT_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            JDBC.sql("""
                INSERT INTO fort_states (
                    fort_id, owner_clan_id, owned_since, siege_at, contract_state, contracted_castle_id, supply_level)
                VALUES (?, ?, ?, ?, 2, 3, 4)\
                """)
                    .param(fortId)
                    .param(clanId)
                    .param(JdbcTimes.utc(NOW.minusSeconds(60)))
                    .param(JdbcTimes.utc(NOW))
                    .update();

            assertThat(repository.findById(fortId))
                    .contains(FortState.builder()
                            .fortId(fortId)
                            .ownerClanId(registry.handleOf(clanId))
                            .lastOwnedTime(NOW.minusSeconds(60))
                            .siegeDate(NOW)
                            .contractState(2)
                            .contractedCastleId(3)
                            .supplyLevel(4)
                            .build());
        }
    }

    @Nested
    class UpdateOwner {
        @Test
        void updateOwner_shouldStoreOwnerAndResetContract() {
            final int fortId = FORT_IDS.incrementAndGet();
            JDBC.sql("""
                INSERT INTO fort_states (fort_id, contract_state, contracted_castle_id, supply_level)
                VALUES (?, 2, 5, 3)\
                """).param(fortId).update();
            final UUID clanId = insertClan();

            repository.updateOwner(fortId, registry.handleOf(clanId), NOW);

            assertThat(repository.findById(fortId))
                    .contains(FortState.builder()
                            .fortId(fortId)
                            .ownerClanId(registry.handleOf(clanId))
                            .lastOwnedTime(NOW)
                            .supplyLevel(3)
                            .build());
        }

        @Test
        void updateOwner_shouldStoreNulls_whenOwnerAndTimeAreZero() {
            final int fortId = FORT_IDS.incrementAndGet();

            repository.updateOwner(fortId, 0, null);

            assertThat(row(fortId))
                    .containsEntry("owner_clan_id", null)
                    .containsEntry("owned_since", null)
                    .containsEntry("contracted_castle_id", null);
        }
    }

    @Nested
    class UpdateContract {
        @Test
        void updateContract_shouldStoreContractedCastle() {
            final int fortId = FORT_IDS.incrementAndGet();

            repository.updateContract(fortId, 2, 7);

            assertThat(row(fortId)).containsEntry("contract_state", 2).containsEntry("contracted_castle_id", 7);
        }

        @Test
        void updateContract_shouldStoreNullCastle_whenCastleIdIsZero() {
            final int fortId = FORT_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO fort_states (fort_id, contract_state, contracted_castle_id) VALUES (?, 1, 4)")
                    .param(fortId)
                    .update();

            repository.updateContract(fortId, 0, 0);

            assertThat(row(fortId)).containsEntry("contract_state", 0).containsEntry("contracted_castle_id", null);
        }
    }

    @Nested
    class UpdateSiegeDate {
        @Test
        void updateSiegeDate_shouldStoreTime() {
            final int fortId = FORT_IDS.incrementAndGet();

            repository.updateSiegeDate(fortId, NOW);

            assertThat(repository.findById(fortId).orElseThrow().siegeDate()).isEqualTo(NOW);
        }

        @Test
        void updateSiegeDate_shouldStoreNull_whenTimeIsZero() {
            final int fortId = FORT_IDS.incrementAndGet();
            repository.updateSiegeDate(fortId, NOW);

            repository.updateSiegeDate(fortId, null);

            assertThat(row(fortId)).containsEntry("siege_at", null);
        }
    }

    @Nested
    class UpdateSupplyLevel {
        @Test
        void updateSupplyLevel_shouldUpsertLevel() {
            final int fortId = FORT_IDS.incrementAndGet();

            repository.updateSupplyLevel(fortId, 2);
            repository.updateSupplyLevel(fortId, 5);

            assertThat(row(fortId)).containsEntry("supply_level", 5);
        }
    }
}
