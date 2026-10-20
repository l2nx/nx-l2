/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.siege;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.siege.ClanHallSiegeAttackerRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanHallSiegeAttackerRepositoryTest extends RepositoryTest {
    private static final AtomicInteger HALL_IDS = new AtomicInteger(15000);

    private final ClanHallSiegeAttackerRepository repository =
            new ClanHallSiegeAttackerRepository(JDBC, JDBC_TEMPLATE, registry);

    private static void insertAttacker(int clanHallId, UUID clanId) {
        JDBC.sql("INSERT INTO clan_hall_siege_attackers (clan_hall_id, clan_id) VALUES (?, ?)")
                .param(clanHallId)
                .param(clanId)
                .update();
    }

    @Nested
    class FindClanIdsByHall {
        @Test
        void findClanIdsByHall_shouldReturnClanHandlesOfHallOnly() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertAttacker(clanHallId, clanId);
            insertAttacker(HALL_IDS.incrementAndGet(), insertClan());

            assertThat(repository.findClanIdsByHall(clanHallId)).containsExactly(registry.handleOf(clanId));
        }
    }

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldReplaceAttackersOfHallOnly() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final int otherHallId = HALL_IDS.incrementAndGet();
            insertAttacker(clanHallId, insertClan());
            insertAttacker(otherHallId, insertClan());
            final List<Integer> expected = List.of(registry.handleOf(insertClan()), registry.handleOf(insertClan()));

            repository.replaceAll(clanHallId, expected);

            assertThat(repository.findClanIdsByHall(clanHallId)).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(repository.findClanIdsByHall(otherHallId)).hasSize(1);
        }

        @Test
        void replaceAll_shouldClearHall_whenNoAttackers() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            insertAttacker(clanHallId, insertClan());

            repository.replaceAll(clanHallId, List.of());

            assertThat(repository.findClanIdsByHall(clanHallId)).isEmpty();
        }
    }
}
