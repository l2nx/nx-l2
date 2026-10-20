/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.siege;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.siege.RainbowSpringsAttackerRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

// deleteAll empties the whole table, so the tests cannot run in parallel.
@Execution(ExecutionMode.SAME_THREAD)
class RainbowSpringsAttackerRepositoryTest extends RepositoryTest {
    private final RainbowSpringsAttackerRepository repository = new RainbowSpringsAttackerRepository(JDBC, registry);

    private static void insertAttacker(UUID clanId, long warDecreeCount) {
        JDBC.sql("INSERT INTO rainbow_springs_attackers (clan_id, war_decree_count) VALUES (?, ?)")
                .param(clanId)
                .param(warDecreeCount)
                .update();
    }

    private static boolean isRegistered(UUID clanId) {
        return JDBC.sql("SELECT EXISTS (SELECT 1 FROM rainbow_springs_attackers WHERE clan_id = ?)")
                .param(clanId)
                .query(Boolean.class)
                .single();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnDecreeCountByClanHandle() {
            final UUID clanId = insertClan();
            insertAttacker(clanId, 12);

            assertThat(repository.findAll()).containsEntry(registry.handleOf(clanId), 12L);
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreClanUuidAndCount() {
            final UUID clanId = insertClan();

            repository.insert(registry.handleOf(clanId), 7);

            assertThat(JDBC.sql("SELECT war_decree_count FROM rainbow_springs_attackers WHERE clan_id = ?")
                            .param(clanId)
                            .query(Long.class)
                            .single())
                    .isEqualTo(7L);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenClan() {
            final UUID clanId = insertClan();
            final UUID otherClanId = insertClan();
            insertAttacker(clanId, 1);
            insertAttacker(otherClanId, 2);

            repository.delete(registry.handleOf(clanId));

            assertThat(isRegistered(clanId)).isFalse();
            assertThat(isRegistered(otherClanId)).isTrue();
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldEmptyTable() {
            insertAttacker(insertClan(), 3);

            repository.deleteAll();

            assertThat(repository.findAll()).isEmpty();
        }
    }
}
