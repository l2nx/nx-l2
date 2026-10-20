/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.FortFunctionRepository;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FortFunctionRepositoryTest extends RepositoryTest {
    private static final AtomicInteger FORT_IDS = new AtomicInteger(3000);

    private final FortFunctionRepository repository = new FortFunctionRepository(JDBC);

    private static ResidenceFunction function(int type, Instant endTime) {
        return ResidenceFunction.builder()
                .type(type)
                .level(1)
                .lease(500)
                .rate(3_600_000L)
                .endTime(endTime)
                .build();
    }

    private static void insertFunction(int fortId, int type) {
        JDBC.sql("""
            INSERT INTO fort_functions (fort_id, function_type, function_level, lease, rate_ms)
            VALUES (?, ?, 1, 500, 3600000)\
            """).param(fortId).param(type).update();
    }

    @Nested
    class Save {
        @Test
        void save_shouldStoreEndTime_whenFeeIsCharged() {
            final int fortId = FORT_IDS.incrementAndGet();
            final ResidenceFunction expected = function(1, NOW);

            repository.save(fortId, expected);

            assertThat(repository.findByFort(fortId)).containsExactly(expected);
        }

        @Test
        void save_shouldStoreNullEndsAt_whenEndTimeIsZero() {
            final int fortId = FORT_IDS.incrementAndGet();
            insertFunction(fortId, 2);

            repository.save(fortId, function(2, null));

            assertThat(JDBC.sql("SELECT ends_at IS NULL FROM fort_functions WHERE fort_id = ?")
                            .param(fortId)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }

    @Nested
    class FindByFort {
        @Test
        void findByFort_shouldReturnZeroEndTime_whenEndsAtIsNull() {
            final int fortId = FORT_IDS.incrementAndGet();
            insertFunction(fortId, 3);

            assertThat(repository.findByFort(fortId)).containsExactly(function(3, null));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenType() {
            final int fortId = FORT_IDS.incrementAndGet();
            insertFunction(fortId, 1);
            insertFunction(fortId, 4);

            repository.delete(fortId, 4);

            assertThat(repository.findByFort(fortId))
                    .extracting(ResidenceFunction::type)
                    .containsExactly(1);
        }
    }
}
