/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.model.SubPledgeRow;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanSubPledgeRepositoryTest extends RepositoryTest {
    private final ClanSubPledgeRepository repository = new ClanSubPledgeRepository(JDBC, registry);

    private UUID leaderOf(UUID clanId, int pledgeType) {
        return JDBC.sql("SELECT leader_character_id FROM clan_subpledges WHERE clan_id = ? AND pledge_type = ?")
                .param(clanId)
                .param(pledgeType)
                .query((rs, rowNum) -> rs.getObject("leader_character_id", UUID.class))
                .list()
                .getFirst();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldMapLeaderToHandleAndMissingLeaderToZero() {
            final UUID clanId = insertClan();
            final UUID leaderId = insertCharacter();
            JDBC.sql("""
                INSERT INTO clan_subpledges (clan_id, pledge_type, name, leader_character_id)
                VALUES (?, -1, 'Academy', NULL), (?, 100, 'Guard', ?)\
                """).param(clanId).param(clanId).param(leaderId).update();

            assertThat(repository.findAll(registry.handleOf(clanId)))
                    .containsExactlyInAnyOrder(
                            SubPledgeRow.builder()
                                    .pledgeType(-1)
                                    .name("Academy")
                                    .leaderId(0)
                                    .build(),
                            SubPledgeRow.builder()
                                    .pledgeType(100)
                                    .name("Guard")
                                    .leaderId(registry.handleOf(leaderId))
                                    .build());
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreLeaderId() {
            final UUID clanId = insertClan();
            final UUID leaderId = insertCharacter();

            repository.insert(registry.handleOf(clanId), 1001, "Knights", registry.handleOf(leaderId));

            assertThat(leaderOf(clanId, 1001)).isEqualTo(leaderId);
        }

        @Test
        void insert_shouldStoreNullLeader_whenLeaderIsZero() {
            final UUID clanId = insertClan();

            repository.insert(registry.handleOf(clanId), -1, "Academy", 0);

            assertThat(leaderOf(clanId, -1)).isNull();
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldWriteNameAndLeader() {
            final UUID clanId = insertClan();
            final UUID leaderId = insertCharacter();
            repository.insert(registry.handleOf(clanId), 200, "Old", 0);

            repository.update(registry.handleOf(clanId), 200, "New", registry.handleOf(leaderId));

            assertThat(repository.findAll(registry.handleOf(clanId)))
                    .containsExactly(SubPledgeRow.builder()
                            .pledgeType(200)
                            .name("New")
                            .leaderId(registry.handleOf(leaderId))
                            .build());
        }

        @Test
        void update_shouldStoreNullLeader_whenLeaderIsZero() {
            final UUID clanId = insertClan();
            repository.insert(registry.handleOf(clanId), 100, "Guard", registry.handleOf(insertCharacter()));

            repository.update(registry.handleOf(clanId), 100, "Guard", 0);

            assertThat(leaderOf(clanId, 100)).isNull();
        }
    }
}
