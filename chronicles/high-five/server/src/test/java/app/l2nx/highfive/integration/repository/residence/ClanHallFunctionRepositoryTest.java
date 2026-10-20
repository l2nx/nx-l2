/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

// deleteOfUnownedHalls touches every row of the table.
@Execution(ExecutionMode.SAME_THREAD)
class ClanHallFunctionRepositoryTest extends RepositoryTest {
    private static final AtomicInteger HALL_IDS = new AtomicInteger(4000);

    private final ClanHallFunctionRepository repository = new ClanHallFunctionRepository(JDBC);

    private static ResidenceFunction function(int type, Instant endTime) {
        return ResidenceFunction.builder()
                .type(type)
                .level(3)
                .lease(2000)
                .rate(604_800_000L)
                .endTime(endTime)
                .build();
    }

    private static void insertFunction(int clanHallId, int type) {
        JDBC.sql("""
            INSERT INTO clan_hall_functions (clan_hall_id, function_type, function_level, lease, rate_ms)
            VALUES (?, ?, 3, 2000, 604800000)\
            """).param(clanHallId).param(type).update();
    }

    @Nested
    class Save {
        @Test
        void save_shouldStoreFunction_whenTypeIsNew() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final ResidenceFunction expected = function(8, NOW);

            repository.save(clanHallId, expected);

            assertThat(repository.findByClanHall(clanHallId)).containsExactly(expected);
        }

        @Test
        void save_shouldOverwriteFunction_whenTypeIsStored() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            insertFunction(clanHallId, 5);
            final ResidenceFunction expected =
                    function(5, null).toBuilder().level(4).build();

            repository.save(clanHallId, expected);

            assertThat(repository.findByClanHall(clanHallId)).containsExactly(expected);
        }
    }

    @Nested
    class FindByClanHall {
        @Test
        void findByClanHall_shouldReturnFunctionsOfHallOnly() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            insertFunction(clanHallId, 1);
            insertFunction(HALL_IDS.incrementAndGet(), 2);

            assertThat(repository.findByClanHall(clanHallId)).containsExactly(function(1, null));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenType() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            insertFunction(clanHallId, 1);
            insertFunction(clanHallId, 2);

            repository.delete(clanHallId, 1);

            assertThat(repository.findByClanHall(clanHallId))
                    .extracting(ResidenceFunction::type)
                    .containsExactly(2);
        }
    }

    @Nested
    class DeleteOfUnownedHalls {
        @Test
        void deleteOfUnownedHalls_shouldKeepFunctionsOfOwnedHalls() {
            final int ownedHallId = HALL_IDS.incrementAndGet();
            final int ownedSiegableHallId = HALL_IDS.incrementAndGet();
            final int freedHallId = HALL_IDS.incrementAndGet();
            final int hallWithoutStateId = HALL_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO clan_hall_states (clan_hall_id, owner_clan_id) VALUES (?, ?), (?, NULL)")
                    .param(ownedHallId)
                    .param(insertClan())
                    .param(freedHallId)
                    .update();
            JDBC.sql("INSERT INTO siegable_clan_hall_states (clan_hall_id, owner_clan_id) VALUES (?, ?)")
                    .param(ownedSiegableHallId)
                    .param(insertClan())
                    .update();
            for (int clanHallId : new int[] {ownedHallId, ownedSiegableHallId, freedHallId, hallWithoutStateId}) {
                insertFunction(clanHallId, 1);
            }

            repository.deleteOfUnownedHalls();

            assertThat(repository.findByClanHall(ownedHallId)).hasSize(1);
            assertThat(repository.findByClanHall(ownedSiegableHallId)).hasSize(1);
            assertThat(repository.findByClanHall(freedHallId)).isEmpty();
            assertThat(repository.findByClanHall(hallWithoutStateId)).isEmpty();
        }
    }
}
