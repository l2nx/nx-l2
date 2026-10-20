/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.mechanics.siege.manor.CropProcure;
import app.l2nx.highfive.mechanics.siege.manor.SeedProduction;
import app.l2nx.highfive.repository.residence.CastleManorRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ListAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

// replaceAll rewrites every row of both tables.
@Execution(ExecutionMode.SAME_THREAD)
class CastleManorRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CASTLE_IDS = new AtomicInteger(8000);

    private final CastleManorRepository repository = new CastleManorRepository(JDBC, JDBC_TEMPLATE);

    private static void insertProduction(int castleId, int seedId, boolean nextPeriod, long amount) {
        JDBC.sql("""
            INSERT INTO castle_manor_productions
                (castle_id, seed_item_template_id, next_period, amount, start_amount, price)
            VALUES (?, ?, ?, ?, 100, 50)\
            """)
                .param(castleId)
                .param(seedId)
                .param(nextPeriod)
                .param(amount)
                .update();
    }

    private static void insertProcurement(int castleId, int cropId, boolean nextPeriod, long amount) {
        JDBC.sql("""
            INSERT INTO castle_manor_procurements
                (castle_id, crop_item_template_id, next_period, amount, start_amount, price, reward_type)
            VALUES (?, ?, ?, ?, 200, 70, 2)\
            """)
                .param(castleId)
                .param(cropId)
                .param(nextPeriod)
                .param(amount)
                .update();
    }

    private static ListAssert<Tuple> assertProductions(List<SeedProduction> actual) {
        return assertThat(actual.stream()
                .map(p -> tuple(p.getId(), p.getAmount(), p.getStartAmount(), p.getPrice()))
                .toList());
    }

    private static ListAssert<Tuple> assertProcurements(List<CropProcure> actual) {
        return assertThat(actual.stream()
                .map(p -> tuple(p.getId(), p.getAmount(), p.getStartAmount(), p.getPrice(), p.getReward()))
                .toList());
    }

    @Nested
    class FindProductions {
        @Test
        void findProductions_shouldReturnRowsOfRequestedPeriod() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertProduction(castleId, 5016, false, 10);
            insertProduction(castleId, 5016, true, 20);

            assertProductions(repository.findProductions(castleId, true)).containsExactly(tuple(5016, 20L, 100L, 50L));
        }
    }

    @Nested
    class FindProcurements {
        @Test
        void findProcurements_shouldReturnRowsOfRequestedPeriod() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertProcurement(castleId, 5073, false, 30);
            insertProcurement(castleId, 5073, true, 40);

            assertProcurements(repository.findProcurements(castleId, false))
                    .containsExactly(tuple(5073, 30L, 200L, 70L, 2));
        }
    }

    @Nested
    class ReplaceNextProductions {
        @Test
        void replaceNextProductions_shouldKeepCurrentPeriod() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertProduction(castleId, 5016, false, 10);
            insertProduction(castleId, 5017, true, 10);

            repository.replaceNextProductions(castleId, List.of(new SeedProduction(5018, 5, 60, 7)));

            assertProductions(repository.findProductions(castleId, true)).containsExactly(tuple(5018, 5L, 7L, 60L));
            assertProductions(repository.findProductions(castleId, false)).hasSize(1);
        }
    }

    @Nested
    class ReplaceNextProcurements {
        @Test
        void replaceNextProcurements_shouldKeepCurrentPeriod() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertProcurement(castleId, 5073, false, 10);
            insertProcurement(castleId, 5074, true, 10);

            repository.replaceNextProcurements(castleId, List.of(new CropProcure(5075, 8, 1, 9, 90)));

            assertProcurements(repository.findProcurements(castleId, true))
                    .containsExactly(tuple(5075, 8L, 9L, 90L, 1));
            assertProcurements(repository.findProcurements(castleId, false)).hasSize(1);
        }
    }

    @Nested
    class UpdateCurrentProductionAmounts {
        @Test
        void updateCurrentProductionAmounts_shouldLeaveNextPeriod() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertProduction(castleId, 5016, false, 10);
            insertProduction(castleId, 5016, true, 10);

            repository.updateCurrentProductionAmounts(castleId, List.of(new SeedProduction(5016, 3, 50, 100)));

            assertProductions(repository.findProductions(castleId, false)).containsExactly(tuple(5016, 3L, 100L, 50L));
            assertProductions(repository.findProductions(castleId, true)).containsExactly(tuple(5016, 10L, 100L, 50L));
        }
    }

    @Nested
    class UpdateCurrentProcurementAmounts {
        @Test
        void updateCurrentProcurementAmounts_shouldLeaveNextPeriod() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertProcurement(castleId, 5073, false, 10);
            insertProcurement(castleId, 5073, true, 10);

            repository.updateCurrentProcurementAmounts(castleId, List.of(new CropProcure(5073, 4, 2, 200, 70)));

            assertProcurements(repository.findProcurements(castleId, false))
                    .containsExactly(tuple(5073, 4L, 200L, 70L, 2));
            assertProcurements(repository.findProcurements(castleId, true))
                    .containsExactly(tuple(5073, 10L, 200L, 70L, 2));
        }
    }

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldReplaceManorOfEveryCastle() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final int otherCastleId = CASTLE_IDS.incrementAndGet();
            insertProduction(otherCastleId, 5016, false, 10);
            insertProcurement(otherCastleId, 5073, true, 10);

            repository.replaceAll(
                    Map.of(castleId, List.of(new SeedProduction(5016, 1, 2, 3))),
                    Map.of(castleId, List.of(new SeedProduction(5017, 4, 5, 6))),
                    Map.of(castleId, List.of(new CropProcure(5073, 7, 1, 8, 9))),
                    Map.of(castleId, List.of(new CropProcure(5074, 10, 2, 11, 12))));

            assertProductions(repository.findProductions(castleId, false)).containsExactly(tuple(5016, 1L, 3L, 2L));
            assertProductions(repository.findProductions(castleId, true)).containsExactly(tuple(5017, 4L, 6L, 5L));
            assertProcurements(repository.findProcurements(castleId, false))
                    .containsExactly(tuple(5073, 7L, 8L, 9L, 1));
            assertProcurements(repository.findProcurements(castleId, true))
                    .containsExactly(tuple(5074, 10L, 11L, 12L, 2));
            assertThat(repository.findProductions(otherCastleId, false)).isEmpty();
            assertThat(repository.findProcurements(otherCastleId, true)).isEmpty();
        }
    }

    @Nested
    class DeleteByCastle {
        @Test
        void deleteByCastle_shouldRemoveBothTablesOfCastleOnly() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final int otherCastleId = CASTLE_IDS.incrementAndGet();
            insertProduction(castleId, 5016, false, 10);
            insertProcurement(castleId, 5073, true, 10);
            insertProduction(otherCastleId, 5016, false, 10);

            repository.deleteByCastle(castleId);

            assertThat(repository.findProductions(castleId, false)).isEmpty();
            assertThat(repository.findProcurements(castleId, true)).isEmpty();
            assertThat(repository.findProductions(otherCastleId, false)).hasSize(1);
        }
    }
}
