/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.CastleFunctionRepository;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CastleFunctionRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CASTLE_IDS = new AtomicInteger(2000);

    private final CastleFunctionRepository repository = new CastleFunctionRepository(JDBC);

    private static ResidenceFunction function(int type, Instant endTime) {
        return ResidenceFunction.builder()
                .type(type)
                .level(2)
                .lease(1000)
                .rate(86_400_000L)
                .endTime(endTime)
                .build();
    }

    @Nested
    class Save {
        @Test
        void save_shouldOverwriteFunction_whenTypeIsStored() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            JDBC.sql("""
                INSERT INTO castle_functions (castle_id, function_type, function_level, lease, rate_ms)
                VALUES (?, 3, 1, 10, 1000)\
                """).param(castleId).update();
            final ResidenceFunction expected = function(3, NOW);

            repository.save(castleId, expected);

            assertThat(repository.findByCastle(castleId)).containsExactly(expected);
        }
    }

    @Nested
    class FindByCastle {
        @Test
        void findByCastle_shouldReturnNullEndTime_whenEndsAtIsNull() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            JDBC.sql("""
                INSERT INTO castle_functions (castle_id, function_type, function_level, lease, rate_ms)
                VALUES (?, 4, 2, 1000, 86400000)\
                """).param(castleId).update();

            assertThat(repository.findByCastle(castleId)).containsExactly(function(4, null));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenType() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            JDBC.sql("""
                INSERT INTO castle_functions (castle_id, function_type, function_level, lease, rate_ms)
                VALUES (?, 1, 2, 1000, 86400000), (?, 2, 2, 1000, 86400000)\
                """).param(castleId).param(castleId).update();

            repository.delete(castleId, 1);

            assertThat(repository.findByCastle(castleId))
                    .extracting(ResidenceFunction::type)
                    .containsExactly(2);
        }
    }
}
