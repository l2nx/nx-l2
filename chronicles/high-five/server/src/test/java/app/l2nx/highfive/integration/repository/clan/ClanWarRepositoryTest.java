/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.clanName;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.setAlliance;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.clan.model.ClanWar;
import app.l2nx.highfive.repository.clan.model.WarOpponent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanWarRepositoryTest extends RepositoryTest {
    private final ClanWarRepository repository = new ClanWarRepository(JDBC, registry);

    private static void insertWar(UUID clanId, UUID enemyClanId) {
        JDBC.sql("INSERT INTO clan_wars (clan_id, enemy_clan_id) VALUES (?, ?)")
                .param(clanId)
                .param(enemyClanId)
                .update();
    }

    private static boolean warExists(UUID clanId, UUID enemyClanId) {
        return JDBC.sql("SELECT count(*) FROM clan_wars WHERE clan_id = ? AND enemy_clan_id = ?")
                        .param(clanId)
                        .param(enemyClanId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    private WarOpponent opponent(UUID clanId) {
        return WarOpponent.builder()
                .clanId(registry.handleOf(clanId))
                .name(clanName(JDBC, clanId))
                .allyId(0)
                .build();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnWarsAsHandles() {
            final UUID clanId = insertClan();
            final UUID enemyClanId = insertClan();
            insertWar(clanId, enemyClanId);

            assertThat(repository.findAll())
                    .contains(ClanWar.builder()
                            .clanId(registry.handleOf(clanId))
                            .enemyClanId(registry.handleOf(enemyClanId))
                            .build());
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldIgnoreDuplicate_whenWarIsAlreadyDeclared() {
            final UUID clanId = insertClan();
            final UUID enemyClanId = insertClan();

            repository.insert(registry.handleOf(clanId), registry.handleOf(enemyClanId));
            repository.insert(registry.handleOf(clanId), registry.handleOf(enemyClanId));

            assertThat(warExists(clanId, enemyClanId)).isTrue();
            assertThat(warExists(enemyClanId, clanId)).isFalse();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOneDirectionOnly() {
            final UUID clanId = insertClan();
            final UUID enemyClanId = insertClan();
            insertWar(clanId, enemyClanId);
            insertWar(enemyClanId, clanId);

            repository.delete(registry.handleOf(clanId), registry.handleOf(enemyClanId));

            assertThat(warExists(clanId, enemyClanId)).isFalse();
            assertThat(warExists(enemyClanId, clanId)).isTrue();
        }
    }

    private UUID clanId;
    private UUID declaredId;
    private UUID attackerId;
    private UUID mutualId;

    private void insertWarScenario() {
        clanId = insertClan();
        declaredId = insertClan();
        attackerId = insertClan();
        mutualId = insertClan();
        insertWar(clanId, declaredId);
        insertWar(attackerId, clanId);
        insertWar(clanId, mutualId);
        insertWar(mutualId, clanId);
    }

    @Nested
    class FindDeclaredOnly {
        @BeforeEach
        void setUp() {
            insertWarScenario();
        }

        @Test
        void findDeclaredOnly_shouldReturnClansThatDidNotDeclareBack() {
            assertThat(repository.findDeclaredOnly(registry.handleOf(clanId))).containsExactly(opponent(declaredId));
        }
    }

    @Nested
    class FindAttackersOnly {
        @BeforeEach
        void setUp() {
            insertWarScenario();
        }

        @Test
        void findAttackersOnly_shouldReturnClansNotDeclaredBack() {
            assertThat(repository.findAttackersOnly(registry.handleOf(clanId))).containsExactly(opponent(attackerId));
        }
    }

    @Nested
    class FindMutual {
        @BeforeEach
        void setUp() {
            insertWarScenario();
        }

        @Test
        void findMutual_shouldReturnClansAtWarBothWays() {
            assertThat(repository.findMutual(registry.handleOf(clanId))).containsExactly(opponent(mutualId));
        }

        @Test
        void findMutual_shouldMapAllianceOfOpponent() {
            final UUID allyLeaderId = insertClan();
            setAlliance(JDBC, mutualId, allyLeaderId, "Ally");

            assertThat(repository.findMutual(registry.handleOf(clanId)))
                    .containsExactly(opponent(mutualId).toBuilder()
                            .allyId(registry.handleOf(allyLeaderId))
                            .allyName("Ally")
                            .build());
        }
    }
}
