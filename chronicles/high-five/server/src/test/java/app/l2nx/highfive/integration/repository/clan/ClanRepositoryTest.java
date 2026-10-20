/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.characterName;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.clanName;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.count;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.insertAuctionBid;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.insertCrest;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.joinClan;
import static app.l2nx.highfive.integration.repository.clan.ClanFixtures.setAlliance;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.clan.model.ClanUpdate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClanRepositoryTest extends RepositoryTest {
    private static final Instant PENALTY_END = NOW.plus(Duration.ofDays(1));
    private static final Instant CHARACTER_PENALTY_END = NOW.plus(Duration.ofDays(2));
    private static final Instant DISSOLVING_END = NOW.plus(Duration.ofDays(3));

    private final ClanRepository repository = new ClanRepository(JDBC, registry);

    private boolean isNull(UUID clanId, String column) {
        return JDBC.sql("SELECT " + column + " IS NULL FROM clans WHERE id = ?")
                .param(clanId)
                .query(Boolean.class)
                .single();
    }

    private <T> T column(UUID clanId, String column, Class<T> type) {
        return JDBC.sql("SELECT " + column + " FROM clans WHERE id = ?")
                .param(clanId)
                .query(type)
                .single();
    }

    private Instant time(UUID clanId, String column) {
        return JDBC.sql("SELECT " + column + " FROM clans WHERE id = ?")
                .param(clanId)
                .query((rs, rowNum) -> JdbcTimes.instant(rs, column))
                .single();
    }

    private ClanRow.ClanRowBuilder newClan() {
        return ClanRow.builder()
                .clanId(registry.allocateTransient())
                .name("n" + EntityIds.next().toString().substring(24))
                .level(0)
                .leaderId(registry.handleOf(insertCharacter()));
    }

    @Nested
    class FindAllClanIds {
        @Test
        void findAllClanIds_shouldReturnHandlesOfStoredClans() {
            final UUID first = insertClan();
            final UUID second = insertClan();

            assertThat(repository.findAllClanIds()).contains(registry.handleOf(first), registry.handleOf(second));
        }
    }

    @Nested
    class Find {
        @Test
        void find_shouldReturnZeroSentinels_whenOptionalColumnsAreNull() {
            final UUID clanId = insertClan();

            final ClanRow actual = repository.find(registry.handleOf(clanId)).orElseThrow();

            assertThat(actual.name()).isEqualTo(clanName(JDBC, clanId));
            assertThat(actual.leaderId())
                    .isEqualTo(registry.handleOf(column(clanId, "leader_character_id", UUID.class)));
            assertThat(actual.newLeaderId()).isZero();
            assertThat(actual.allyId()).isZero();
            assertThat(actual.allyName()).isNull();
            assertThat(actual.crestId()).isZero();
            assertThat(actual.crestLargeId()).isZero();
            assertThat(actual.allyCrestId()).isZero();
            assertThat(actual.bidClanHallId()).isZero();
            assertThat(actual.allyPenaltyEndsAt()).isNull();
            assertThat(actual.allyPenaltyType()).isZero();
            assertThat(actual.characterPenaltyEndsAt()).isNull();
            assertThat(actual.dissolvingEndsAt()).isNull();
        }

        @Test
        void find_shouldMapEveryColumn_whenAllAreSet() {
            final UUID clanId = insertClan();
            final UUID allyId = insertClan();
            final UUID newLeaderId = insertCharacter();
            final int crestId = insertCrest(JDBC);
            final int largeCrestId = insertCrest(JDBC);
            final int allyCrestId = insertCrest(JDBC);
            final int clanHallId = insertAuctionBid(JDBC, clanId, NOW);
            JDBC.sql("""
                UPDATE clans
                SET clan_level = 5, reputation_score = -10, blood_alliance_count = 2, blood_oath_count = 3,
                    new_leader_character_id = ?, ally_clan_id = ?, ally_name = 'Ally',
                    crest_id = ?, crest_large_id = ?, ally_crest_id = ?, bid_clan_hall_id = ?,
                    ally_penalty_ends_at = ?, ally_penalty_type = 3,
                    character_penalty_ends_at = ?, dissolving_ends_at = ?
                WHERE id = ?\
                """)
                    .param(newLeaderId)
                    .param(allyId)
                    .param(crestId)
                    .param(largeCrestId)
                    .param(allyCrestId)
                    .param(clanHallId)
                    .param(JdbcTimes.utc(PENALTY_END))
                    .param(JdbcTimes.utc(CHARACTER_PENALTY_END))
                    .param(JdbcTimes.utc(DISSOLVING_END))
                    .param(clanId)
                    .update();

            final ClanRow actual = repository.find(registry.handleOf(clanId)).orElseThrow();

            assertThat(actual)
                    .isEqualTo(ClanRow.builder()
                            .clanId(registry.handleOf(clanId))
                            .name(clanName(JDBC, clanId))
                            .level(5)
                            .reputationScore(-10)
                            .bloodAllianceCount(2)
                            .bloodOathCount(3)
                            .leaderId(registry.handleOf(column(clanId, "leader_character_id", UUID.class)))
                            .newLeaderId(registry.handleOf(newLeaderId))
                            .allyId(registry.handleOf(allyId))
                            .allyName("Ally")
                            .crestId(crestId)
                            .crestLargeId(largeCrestId)
                            .allyCrestId(allyCrestId)
                            .bidClanHallId(clanHallId)
                            .allyPenaltyEndsAt(PENALTY_END)
                            .allyPenaltyType(3)
                            .characterPenaltyEndsAt(CHARACTER_PENALTY_END)
                            .dissolvingEndsAt(DISSOLVING_END)
                            .build());
        }

        @Test
        void find_shouldReturnEmpty_whenClanIsNotStored() {
            assertThat(repository.find(registry.allocateTransient())).isEmpty();
        }
    }

    @Nested
    class FindNameByCharacterName {
        @Test
        void findNameByCharacterName_shouldReturnClanName_whenCharacterIsMember() {
            final UUID clanId = insertClan();
            final UUID characterId = insertCharacter();
            joinClan(JDBC, characterId, clanId);

            assertThat(repository.findNameByCharacterName(characterName(JDBC, characterId)))
                    .contains(clanName(JDBC, clanId));
        }

        @Test
        void findNameByCharacterName_shouldReturnEmpty_whenCharacterHasNoClan() {
            assertThat(repository.findNameByCharacterName(characterName(JDBC, insertCharacter())))
                    .isEmpty();
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreNulls_whenSentinelsAreZeroOrEmpty() {
            final ClanRow clan = newClan().allyName("").build();

            repository.insert(clan);

            final UUID clanId = registry.idOf(clan.clanId());
            assertThat(column(clanId, "name", String.class)).isEqualTo(clan.name());
            assertThat(column(clanId, "leader_character_id", UUID.class)).isEqualTo(registry.idOf(clan.leaderId()));
            for (String nullable : new String[] {
                "new_leader_character_id",
                "ally_clan_id",
                "ally_name",
                "crest_id",
                "crest_large_id",
                "ally_crest_id",
                "bid_clan_hall_id",
                "ally_penalty_ends_at",
                "ally_penalty_type",
                "character_penalty_ends_at",
                "dissolving_ends_at"
            }) {
                assertThat(isNull(clanId, nullable)).as(nullable).isTrue();
            }
        }

        @Test
        void insert_shouldStoreEveryColumn_whenAllAreSet() {
            final int crestId = insertCrest(JDBC);
            final int clanHandle = registry.allocateTransient();
            final UUID newLeaderId = insertCharacter();
            final ClanRow clan = newClan()
                    .clanId(clanHandle)
                    .level(3)
                    .reputationScore(150)
                    .bloodAllianceCount(1)
                    .bloodOathCount(2)
                    .newLeaderId(registry.handleOf(newLeaderId))
                    .allyId(clanHandle)
                    .allyName("Ally")
                    .crestId(crestId)
                    .crestLargeId(crestId)
                    .allyCrestId(crestId)
                    .allyPenaltyEndsAt(PENALTY_END)
                    .allyPenaltyType(4)
                    .characterPenaltyEndsAt(CHARACTER_PENALTY_END)
                    .dissolvingEndsAt(DISSOLVING_END)
                    .build();

            repository.insert(clan);

            assertThat(repository.find(clanHandle)).contains(clan);
            final UUID clanId = registry.idOf(clanHandle);
            assertThat(column(clanId, "ally_clan_id", UUID.class)).isEqualTo(clanId);
            assertThat(column(clanId, "new_leader_character_id", UUID.class)).isEqualTo(newLeaderId);
            assertThat(time(clanId, "dissolving_ends_at")).isEqualTo(DISSOLVING_END);
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldWriteLeadershipAllianceAndPenalties() {
            final UUID clanId = insertClan();
            final UUID allyId = insertClan();
            final UUID leaderId = insertCharacter();
            final UUID newLeaderId = insertCharacter();

            repository.update(ClanUpdate.builder()
                    .clanId(registry.handleOf(clanId))
                    .leaderId(registry.handleOf(leaderId))
                    .newLeaderId(registry.handleOf(newLeaderId))
                    .allyId(registry.handleOf(allyId))
                    .allyName("Ally")
                    .reputationScore(-5)
                    .allyPenaltyEndsAt(PENALTY_END)
                    .allyPenaltyType(2)
                    .characterPenaltyEndsAt(CHARACTER_PENALTY_END)
                    .dissolvingEndsAt(DISSOLVING_END)
                    .build());

            assertThat(column(clanId, "leader_character_id", UUID.class)).isEqualTo(leaderId);
            assertThat(column(clanId, "new_leader_character_id", UUID.class)).isEqualTo(newLeaderId);
            assertThat(column(clanId, "ally_clan_id", UUID.class)).isEqualTo(allyId);
            assertThat(column(clanId, "ally_name", String.class)).isEqualTo("Ally");
            assertThat(column(clanId, "reputation_score", Integer.class)).isEqualTo(-5);
            assertThat(time(clanId, "ally_penalty_ends_at")).isEqualTo(PENALTY_END);
            assertThat(column(clanId, "ally_penalty_type", Integer.class)).isEqualTo(2);
            assertThat(time(clanId, "character_penalty_ends_at")).isEqualTo(CHARACTER_PENALTY_END);
            assertThat(time(clanId, "dissolving_ends_at")).isEqualTo(DISSOLVING_END);
        }

        @Test
        void update_shouldClearToNull_whenValuesAreZero() {
            final UUID clanId = insertClan();
            final UUID allyId = insertClan();
            setAlliance(JDBC, clanId, allyId, "Ally");
            final int leaderHandle = registry.handleOf(column(clanId, "leader_character_id", UUID.class));

            repository.update(ClanUpdate.builder()
                    .clanId(registry.handleOf(clanId))
                    .leaderId(leaderHandle)
                    .allyName("")
                    .allyPenaltyType(1)
                    .build());

            assertThat(isNull(clanId, "ally_clan_id")).isTrue();
            assertThat(isNull(clanId, "ally_name")).isTrue();
            assertThat(isNull(clanId, "new_leader_character_id")).isTrue();
            assertThat(isNull(clanId, "ally_penalty_ends_at")).isTrue();
            assertThat(isNull(clanId, "ally_penalty_type")).isTrue();
            assertThat(isNull(clanId, "character_penalty_ends_at")).isTrue();
            assertThat(isNull(clanId, "dissolving_ends_at")).isTrue();
        }
    }

    @Nested
    class UpdateBloodAllianceCount {
        @Test
        void updateBloodAllianceCount_shouldWriteCount() {
            final UUID clanId = insertClan();

            repository.updateBloodAllianceCount(registry.handleOf(clanId), 4);

            assertThat(column(clanId, "blood_alliance_count", Integer.class)).isEqualTo(4);
        }
    }

    @Nested
    class UpdateBloodOathCount {
        @Test
        void updateBloodOathCount_shouldWriteCount() {
            final UUID clanId = insertClan();

            repository.updateBloodOathCount(registry.handleOf(clanId), 6);

            assertThat(column(clanId, "blood_oath_count", Integer.class)).isEqualTo(6);
        }
    }

    @Nested
    class UpdateLevel {
        @Test
        void updateLevel_shouldWriteLevel() {
            final UUID clanId = insertClan();

            repository.updateLevel(registry.handleOf(clanId), 8);

            assertThat(column(clanId, "clan_level", Integer.class)).isEqualTo(8);
        }
    }

    @Nested
    class UpdateBidClanHall {
        @Test
        void updateBidClanHall_shouldWriteHall_whenBidExists() {
            final UUID clanId = insertClan();
            final int clanHallId = insertAuctionBid(JDBC, clanId, NOW);

            repository.updateBidClanHall(registry.handleOf(clanId), clanHallId);

            assertThat(column(clanId, "bid_clan_hall_id", Integer.class)).isEqualTo(clanHallId);
        }

        @Test
        void updateBidClanHall_shouldStoreNull_whenHallIsZero() {
            final UUID clanId = insertClan();
            final int clanHallId = insertAuctionBid(JDBC, clanId, NOW);
            repository.updateBidClanHall(registry.handleOf(clanId), clanHallId);

            repository.updateBidClanHall(registry.handleOf(clanId), 0);

            assertThat(isNull(clanId, "bid_clan_hall_id")).isTrue();
        }
    }

    @Nested
    class UpdateCrests {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"crest_id", "crest_large_id", "ally_crest_id"})
        void updateCrest_shouldWriteCrestAndClearOnZero(String column) {
            final UUID clanId = insertClan();
            final int clanHandle = registry.handleOf(clanId);
            final int crestId = insertCrest(JDBC);

            updateCrest(column, clanHandle, crestId);
            assertThat(column(clanId, column, Integer.class)).isEqualTo(crestId);

            updateCrest(column, clanHandle, 0);
            assertThat(isNull(clanId, column)).isTrue();
        }

        private void updateCrest(String column, int clanHandle, int crestId) {
            switch (column) {
                case "crest_id" -> repository.updateCrest(clanHandle, crestId);
                case "crest_large_id" -> repository.updateLargeCrest(clanHandle, crestId);
                default -> repository.updateAllyCrest(clanHandle, crestId);
            }
        }
    }

    @Nested
    class UpdateAllyCrestOfAlliance {
        @Test
        void updateAllyCrestOfAlliance_shouldWriteCrestOfMemberClansOnly() {
            final UUID leaderClanId = insertClan();
            final UUID memberClanId = insertClan();
            final UUID otherClanId = insertClan();
            setAlliance(JDBC, leaderClanId, leaderClanId, "Ally");
            setAlliance(JDBC, memberClanId, leaderClanId, "Ally");
            final int crestId = insertCrest(JDBC);

            repository.updateAllyCrestOfAlliance(registry.handleOf(leaderClanId), crestId);

            assertThat(column(leaderClanId, "ally_crest_id", Integer.class)).isEqualTo(crestId);
            assertThat(column(memberClanId, "ally_crest_id", Integer.class)).isEqualTo(crestId);
            assertThat(isNull(otherClanId, "ally_crest_id")).isTrue();
        }
    }

    @Nested
    class ClearAllianceLeftovers {
        @Test
        void clearAllianceLeftovers_shouldClearNameAndCrest_whenAllianceLeaderWasDeleted() {
            final UUID leaderClanId = insertClan();
            final UUID memberClanId = insertClan();
            final UUID otherAllianceClanId = insertClan();
            setAlliance(JDBC, memberClanId, leaderClanId, "Gone");
            setAlliance(JDBC, otherAllianceClanId, otherAllianceClanId, "Kept");
            JDBC.sql("UPDATE clans SET ally_crest_id = ? WHERE id = ?")
                    .param(insertCrest(JDBC))
                    .param(memberClanId)
                    .update();
            JDBC.sql("DELETE FROM clans WHERE id = ?").param(leaderClanId).update();

            assertThat(repository.clearAllianceLeftovers()).isPositive();

            assertThat(isNull(memberClanId, "ally_name")).isTrue();
            assertThat(isNull(memberClanId, "ally_crest_id")).isTrue();
            assertThat(column(otherAllianceClanId, "ally_name", String.class)).isEqualTo("Kept");
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveClanWithItsRowsAndKeepMembers() {
            final UUID clanId = insertClan();
            final UUID enemyClanId = insertClan();
            final UUID memberId = insertCharacter();
            joinClan(JDBC, memberId, clanId);
            JDBC.sql("INSERT INTO clan_notices (clan_id, enabled, notice) VALUES (?, true, 'hi')")
                    .param(clanId)
                    .update();
            JDBC.sql("INSERT INTO clan_rank_privileges (clan_id, power_grade, privilege_mask) VALUES (?, 1, 7)")
                    .param(clanId)
                    .update();
            JDBC.sql("INSERT INTO clan_skills (clan_id, skill_id, skill_level) VALUES (?, 370, 1)")
                    .param(clanId)
                    .update();
            JDBC.sql("INSERT INTO clan_subpledges (clan_id, pledge_type, name) VALUES (?, -1, 'Academy')")
                    .param(clanId)
                    .update();
            JDBC.sql("INSERT INTO clan_wars (clan_id, enemy_clan_id) VALUES (?, ?), (?, ?)")
                    .param(clanId)
                    .param(enemyClanId)
                    .param(enemyClanId)
                    .param(clanId)
                    .update();

            repository.delete(registry.handleOf(clanId));

            assertThat(JDBC.sql("SELECT count(*) FROM clans WHERE id = ?")
                            .param(clanId)
                            .query(Long.class)
                            .single())
                    .isZero();
            for (String table :
                    new String[] {"clan_notices", "clan_rank_privileges", "clan_skills", "clan_subpledges", "clan_wars"
                    }) {
                assertThat(count(JDBC, table, clanId)).as(table).isZero();
            }
            assertThat(JDBC.sql("SELECT count(*) FROM clan_wars WHERE enemy_clan_id = ?")
                            .param(clanId)
                            .query(Long.class)
                            .single())
                    .isZero();
            assertThat(JDBC.sql("SELECT clan_id IS NULL FROM characters WHERE id = ?")
                            .param(memberId)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }
}
