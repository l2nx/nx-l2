/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.ClanHallState;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanHallStateRepositoryTest extends RepositoryTest {
    private static final AtomicInteger HALL_IDS = new AtomicInteger(11000);

    private final ClanHallStateRepository repository = new ClanHallStateRepository(JDBC, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnZeroSentinels_whenColumnsAreNull() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO clan_hall_states (clan_hall_id) VALUES (?)")
                    .param(clanHallId)
                    .update();

            assertThat(repository.findAll())
                    .contains(ClanHallState.builder().clanHallId(clanHallId).build());
        }

        @Test
        void findAll_shouldReturnOwnerHandle_whenHallIsOwned() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            JDBC.sql("INSERT INTO clan_hall_states (clan_hall_id, owner_clan_id, paid_until, paid) VALUES (?,"
                            + " ?, ?, true)")
                    .param(clanHallId)
                    .param(clanId)
                    .param(JdbcTimes.utc(NOW))
                    .update();

            assertThat(repository.findAll())
                    .contains(ClanHallState.builder()
                            .clanHallId(clanHallId)
                            .ownerClanId(registry.handleOf(clanId))
                            .paidUntil(NOW)
                            .paid(true)
                            .build());
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldCreateRow_whenNoState() {
            final ClanHallState expected = ClanHallState.builder()
                    .clanHallId(HALL_IDS.incrementAndGet())
                    .ownerClanId(registry.handleOf(insertClan()))
                    .paidUntil(NOW)
                    .paid(true)
                    .build();

            repository.save(expected);

            assertThat(repository.findAll()).contains(expected);
        }

        @Test
        void save_shouldStoreNulls_whenHallIsFreed() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO clan_hall_states (clan_hall_id, owner_clan_id, paid_until, paid) VALUES (?,"
                            + " ?, ?, true)")
                    .param(clanHallId)
                    .param(insertClan())
                    .param(JdbcTimes.utc(NOW))
                    .update();

            repository.save(ClanHallState.builder().clanHallId(clanHallId).build());

            assertThat(JDBC.sql("""
                SELECT owner_clan_id IS NULL AND paid_until IS NULL AND NOT paid
                FROM clan_hall_states
                WHERE clan_hall_id = ?\
                """).param(clanHallId).query(Boolean.class).single())
                    .isTrue();
        }
    }
}
