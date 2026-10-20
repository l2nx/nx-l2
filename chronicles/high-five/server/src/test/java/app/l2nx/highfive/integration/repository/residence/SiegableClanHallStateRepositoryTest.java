/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.SiegableClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.SiegableClanHallState;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SiegableClanHallStateRepositoryTest extends RepositoryTest {
    private static final AtomicInteger HALL_IDS = new AtomicInteger(12000);

    private final SiegableClanHallStateRepository repository = new SiegableClanHallStateRepository(JDBC, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnZeroSentinels_whenColumnsAreNull() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO siegable_clan_hall_states (clan_hall_id) VALUES (?)")
                    .param(clanHallId)
                    .update();

            assertThat(repository.findAll())
                    .contains(SiegableClanHallState.builder()
                            .clanHallId(clanHallId)
                            .build());
        }

        @Test
        void findAll_shouldReturnOwnerHandleAndNextSiege_whenSet() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            JDBC.sql("INSERT INTO siegable_clan_hall_states (clan_hall_id, owner_clan_id, next_siege_at) VALUES"
                            + " (?, ?, ?)")
                    .param(clanHallId)
                    .param(clanId)
                    .param(JdbcTimes.utc(NOW))
                    .update();

            assertThat(repository.findAll())
                    .contains(SiegableClanHallState.builder()
                            .clanHallId(clanHallId)
                            .ownerClanId(registry.handleOf(clanId))
                            .nextSiege(NOW)
                            .build());
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldCreateRow_whenNoState() {
            final SiegableClanHallState expected = SiegableClanHallState.builder()
                    .clanHallId(HALL_IDS.incrementAndGet())
                    .ownerClanId(registry.handleOf(insertClan()))
                    .nextSiege(NOW)
                    .build();

            repository.save(expected);

            assertThat(repository.findAll()).contains(expected);
        }

        @Test
        void save_shouldStoreNulls_whenOwnerAndSiegeAreZero() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO siegable_clan_hall_states (clan_hall_id, owner_clan_id, next_siege_at) VALUES"
                            + " (?, ?, ?)")
                    .param(clanHallId)
                    .param(insertClan())
                    .param(JdbcTimes.utc(NOW))
                    .update();

            repository.save(
                    SiegableClanHallState.builder().clanHallId(clanHallId).build());

            assertThat(JDBC.sql("""
                SELECT owner_clan_id IS NULL AND next_siege_at IS NULL
                FROM siegable_clan_hall_states
                WHERE clan_hall_id = ?\
                """).param(clanHallId).query(Boolean.class).single())
                    .isTrue();
        }
    }
}
