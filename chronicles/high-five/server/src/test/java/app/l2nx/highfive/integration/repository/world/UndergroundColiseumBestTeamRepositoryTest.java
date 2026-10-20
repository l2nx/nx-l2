/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.world.UndergroundColiseumBestTeamRepository;
import app.l2nx.highfive.repository.world.model.UndergroundColiseumBestTeam;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UndergroundColiseumBestTeamRepositoryTest extends RepositoryTest {
    private final UndergroundColiseumBestTeamRepository repository =
            new UndergroundColiseumBestTeamRepository(JDBC, registry);

    private static String nameOf(UUID characterId) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    private UndergroundColiseumBestTeam team(int arenaId, UUID leader, int wins) {
        return UndergroundColiseumBestTeam.builder()
                .arenaId(arenaId)
                .leaderId(registry.handleOf(leader))
                .leaderName(nameOf(leader))
                .wins(wins)
                .build();
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsertTeam_whenArenaHasNone() {
            final UndergroundColiseumBestTeam expected = team(1, insertCharacter(), 3);

            repository.save(expected);

            assertThat(repository.findAll()).contains(expected);
        }

        @Test
        void save_shouldReplaceLeaderAndWins_whenArenaHasTeam() {
            repository.save(team(2, insertCharacter(), 3));
            final UndergroundColiseumBestTeam expected = team(2, insertCharacter(), 5);

            repository.save(expected);

            assertThat(repository.findAll())
                    .filteredOn(stored -> stored.arenaId() == 2)
                    .containsExactly(expected);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldResolveLeaderHandleAndName_whenStored() {
            final UUID leader = insertCharacter();
            JDBC.sql("INSERT INTO underground_coliseum_best_teams (arena_id, leader_character_id, wins) VALUES"
                            + " (3, ?, 7)")
                    .param(leader)
                    .update();

            assertThat(repository.findAll()).contains(team(3, leader, 7));
        }
    }
}
