/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.BirthdayCharacter;
import app.l2nx.highfive.repository.character.model.CharacterAccess;
import app.l2nx.highfive.repository.character.model.CharacterClanAlly;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterSummary;
import app.l2nx.highfive.repository.character.model.Faction;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock("characters.online_status")
class CharacterRepositoryTest extends RepositoryTest {
    private final CharacterRepository repository = new CharacterRepository(JDBC, registry);

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreNullForLegacySentinels() {
            final CharacterRow row = newRow(registry.allocateTransient(), EntityIds.next());

            repository.insert(row);

            final Map<String, Object> actual = character(registry.idOf(row.objectId()));
            assertThat(actual)
                    .containsEntry("title", null)
                    .containsEntry("faction", null)
                    .containsEntry("language", null)
                    .containsEntry("clan_id", null)
                    .containsEntry("power_grade", null)
                    .containsEntry("academy_join_level", null)
                    .containsEntry("apprentice_id", null)
                    .containsEntry("sponsor_id", null)
                    .containsEntry("clan_join_penalty_ends_at", null)
                    .containsEntry("delete_at", null)
                    .containsEntry("race", "DARK_ELF")
                    .containsEntry("sex", "FEMALE")
                    .containsEntry("online_status", "ONLINE")
                    .containsEntry("account_id", row.accountId());
        }

        @Test
        void insert_shouldStoreReferences_whenSet() {
            final UUID clanId = insertClan();
            final UUID sponsorId = insertCharacter();
            final CharacterRow row = newRow(registry.allocateTransient(), EntityIds.next()).toBuilder()
                    .title("Lord")
                    .language("en")
                    .faction(Faction.EVIL)
                    .clanId(registry.handleOf(clanId))
                    .sponsorId(registry.handleOf(sponsorId))
                    .powerGrade(3)
                    .clanJoinPenaltyEndsAt(NOW)
                    .build();

            repository.insert(row);

            assertThat(character(registry.idOf(row.objectId())))
                    .containsEntry("title", "Lord")
                    .containsEntry("faction", "EVIL")
                    .containsEntry("clan_id", clanId)
                    .containsEntry("sponsor_id", sponsorId)
                    .containsEntry("power_grade", 3);
            assertThat(repository.findById(row.objectId())).get().isEqualTo(row);
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldWriteTheRow_whenCharacterExists() {
            final int objectId = registry.handleOf(insertCharacter());
            final CharacterRow stored = repository.findById(objectId).orElseThrow();
            final CharacterRow expected = stored.toBuilder()
                    .name(stored.name() + "x")
                    .level(52)
                    .currentHp(70.4)
                    .x(-84318)
                    .heading(1200)
                    .onlineTimeSeconds(3600)
                    .noble(true)
                    .language("en")
                    .deleteAt(NOW.plusSeconds(60))
                    .build();

            repository.update(expected);

            assertThat(repository.findById(objectId))
                    .get()
                    .isEqualTo(expected.toBuilder().currentHp(70).build());
        }

        @Test
        void update_shouldKeepCreationAndLastAccess() {
            final UUID id = insertCharacter();
            final int objectId = registry.handleOf(id);
            final CharacterRow stored = repository.findById(objectId).orElseThrow();

            repository.update(stored.toBuilder()
                    .createdAt(Instant.EPOCH)
                    .lastAccessAt(Instant.EPOCH)
                    .build());

            assertThat(character(id))
                    .containsEntry("created_at", JdbcTimes.utc(NOW))
                    .containsEntry("last_access_at", JdbcTimes.utc(NOW));
        }
    }

    @Nested
    class FindById {
        @Test
        void findById_shouldMapNullsToLegacySentinels() {
            final UUID id = insertCharacter();

            final CharacterRow actual =
                    repository.findById(registry.handleOf(id)).orElseThrow();

            assertThat(actual)
                    .extracting(
                            CharacterRow::title,
                            CharacterRow::race,
                            CharacterRow::sex,
                            CharacterRow::clanId,
                            CharacterRow::powerGrade,
                            CharacterRow::deleteAt,
                            CharacterRow::faction,
                            CharacterRow::onlineStatus,
                            CharacterRow::createdAt)
                    .containsExactly(null, Race.HUMAN, Sex.MALE, 0, 0, null, null, OnlineStatus.OFFLINE, NOW);
        }

        @Test
        void findById_shouldBeEmpty_whenCharacterMissing() {
            assertThat(repository.findById(registry.allocateTransient())).isEmpty();
        }
    }

    @Nested
    class FindByAccount {
        @Test
        void findByAccount_shouldReturnOldestFirst() {
            final UUID accountId = EntityIds.next();
            final UUID newer = insertCharacter(accountId, NOW);
            final UUID older = insertCharacter(accountId, NOW.minusSeconds(60));
            insertCharacter();

            final List<CharacterRow> actual = repository.findByAccount(accountId);

            assertThat(actual)
                    .extracting(CharacterRow::objectId)
                    .containsExactly(registry.handleOf(older), registry.handleOf(newer));
        }
    }

    @Nested
    class FindOtherNames {
        @Test
        void findOtherNames_shouldSkipTheExcludedCharacter() {
            final UUID accountId = EntityIds.next();
            final UUID self = insertCharacter(accountId, NOW);
            final UUID other = insertCharacter(accountId, NOW);

            final Map<Integer, String> actual = repository.findOtherNames(accountId, registry.handleOf(self));

            assertThat(actual).containsExactly(Map.entry(registry.handleOf(other), name(other)));
        }
    }

    @Nested
    class UpdateOnlineStatus {
        @Test
        void updateOnlineStatus_shouldWriteStatusAndLastAccess() {
            final UUID id = insertCharacter();
            final Instant lastAccess = NOW.plusSeconds(30);

            repository.updateOnlineStatus(registry.handleOf(id), OnlineStatus.DETACHED, lastAccess);

            assertThat(character(id))
                    .containsEntry("online_status", "DETACHED")
                    .containsEntry("last_access_at", JdbcTimes.utc(lastAccess));
        }
    }

    @Nested
    class ResetOnlineStatus {
        @Test
        void resetOnlineStatus_shouldMarkEveryCharacterOffline() {
            final UUID id = insertCharacter();
            repository.updateOnlineStatus(registry.handleOf(id), OnlineStatus.ONLINE, NOW);

            repository.resetOnlineStatus();

            assertThat(character(id)).containsEntry("online_status", "OFFLINE");
        }
    }

    @Nested
    class FindAllAccess {
        @Test
        void findAllAccess_shouldReturnEveryCharacter() {
            final UUID id = insertCharacter();
            setColumn(id, "access_level", -1);

            final List<CharacterAccess> actual = repository.findAllAccess();

            assertThat(actual).contains(access(registry.handleOf(id), name(id), -1));
        }
    }

    @Nested
    class FindAccessByName {
        @Test
        void findAccessByName_shouldIgnoreCase() {
            final UUID id = insertCharacter();

            final var actual = repository.findAccessByName(name(id).toUpperCase());

            assertThat(actual).get().isEqualTo(access(registry.handleOf(id), name(id), 0));
        }

        @Test
        void findAccessByName_shouldBeEmpty_whenNameUnknown() {
            assertThat(repository.findAccessByName("nobody")).isEmpty();
        }
    }

    @Nested
    class FindAccessById {
        @Test
        void findAccessById_shouldReturnNameAndAccessLevel() {
            final UUID id = insertCharacter();
            setColumn(id, "access_level", 8);

            assertThat(repository.findAccessById(registry.handleOf(id)))
                    .get()
                    .isEqualTo(access(registry.handleOf(id), name(id), 8));
        }
    }

    @Nested
    class ExistsByName {
        @Test
        void existsByName_shouldBeTrue_whenNameTakenInAnotherCase() {
            final UUID id = insertCharacter();

            assertThat(repository.existsByName(name(id).toUpperCase())).isTrue();
        }

        @Test
        void existsByName_shouldBeFalse_whenNameFree() {
            assertThat(repository.existsByName("nobody")).isFalse();
        }
    }

    @Nested
    class CountByAccount {
        @Test
        void countByAccount_shouldCountTheAccountCharacters() {
            final UUID accountId = EntityIds.next();
            insertCharacter(accountId, NOW);
            insertCharacter(accountId, NOW);

            assertThat(repository.countByAccount(accountId)).isEqualTo(2);
        }
    }

    @Nested
    class FindDeleteTimes {
        @Test
        void findDeleteTimes_shouldReturnOnlyCharactersMarkedForDeletion() {
            final UUID accountId = EntityIds.next();
            final UUID marked = insertCharacter(accountId, NOW);
            insertCharacter(accountId, NOW);
            repository.updateDeleteTime(registry.handleOf(marked), NOW.plusSeconds(60));

            assertThat(repository.findDeleteTimes(accountId)).containsExactly(NOW.plusSeconds(60));
        }
    }

    @Nested
    class FindSummary {
        @Test
        void findSummary_shouldReturnStatusClassAndLevel() {
            final UUID id = insertCharacter();
            repository.updateOnlineStatus(registry.handleOf(id), OnlineStatus.ONLINE, NOW);

            final var actual = repository.findSummary(registry.handleOf(id));

            assertThat(actual).get().isEqualTo(summary(registry.handleOf(id), name(id), OnlineStatus.ONLINE, 0, 1));
        }
    }

    @Nested
    class FindClanId {
        @Test
        void findClanId_shouldReturnClanHandle_whenMember() {
            final UUID clanId = insertClan();
            final UUID id = insertCharacter();
            setColumn(id, "clan_id", clanId);

            assertThat(repository.findClanId(registry.handleOf(id))).isEqualTo(registry.handleOf(clanId));
        }

        @Test
        void findClanId_shouldBeZero_whenNoClan() {
            assertThat(repository.findClanId(registry.handleOf(insertCharacter())))
                    .isZero();
        }

        @Test
        void findClanId_shouldBeZero_whenCharacterMissing() {
            assertThat(repository.findClanId(registry.allocateTransient())).isZero();
        }
    }

    @Nested
    class FindAccountId {
        @Test
        void findAccountId_shouldReturnAccountOfCharacter() {
            final UUID accountId = EntityIds.next();
            final UUID id = insertCharacter(accountId, NOW);

            assertThat(repository.findAccountId(registry.handleOf(id))).contains(accountId);
        }

        @Test
        void findAccountId_shouldBeEmpty_whenCharacterMissing() {
            assertThat(repository.findAccountId(registry.allocateTransient())).isEmpty();
        }
    }

    @Nested
    class UpdateDeleteTime {
        @Test
        void updateDeleteTime_shouldStoreTime() {
            final UUID id = insertCharacter();

            repository.updateDeleteTime(registry.handleOf(id), NOW.plusSeconds(60));

            assertThat(character(id)).containsEntry("delete_at", JdbcTimes.utc(NOW.plusSeconds(60)));
        }

        @Test
        void updateDeleteTime_shouldStoreNull_whenDeleteTimeIsNull() {
            final UUID id = insertCharacter();
            repository.updateDeleteTime(registry.handleOf(id), NOW);

            repository.updateDeleteTime(registry.handleOf(id), null);

            assertThat(character(id)).containsEntry("delete_at", null);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldCascadeToChildRows() {
            final UUID id = insertCharacter();
            JDBC.sql("INSERT INTO character_variables (character_id, name, content) VALUES (?, 'a', 'b')")
                    .param(id)
                    .update();

            repository.delete(registry.handleOf(id));

            assertThat(count("characters", id)).isZero();
            assertThat(count("character_variables", id)).isZero();
        }
    }

    @Nested
    class UpdateKarmaAndPkKills {
        @Test
        void updateKarmaAndPkKills_shouldWriteBoth() {
            final UUID id = insertCharacter();

            final boolean actual = repository.updateKarmaAndPkKills(registry.handleOf(id), 900, 7);

            assertThat(actual).isTrue();
            assertThat(character(id)).containsEntry("karma", 900).containsEntry("pk_kills", 7);
        }

        @Test
        void updateKarmaAndPkKills_shouldBeFalse_whenCharacterMissing() {
            assertThat(repository.updateKarmaAndPkKills(registry.allocateTransient(), 1, 1))
                    .isFalse();
        }
    }

    @Nested
    class UpdateAccessLevel {
        @Test
        void updateAccessLevel_shouldWriteLevel() {
            final UUID id = insertCharacter();

            repository.updateAccessLevel(registry.handleOf(id), -1);

            assertThat(character(id)).containsEntry("access_level", -1);
        }
    }

    @Nested
    class UpdateAccessLevelByName {
        @Test
        void updateAccessLevelByName_shouldWriteLevel_whenNameExists() {
            final UUID id = insertCharacter();

            assertThat(repository.updateAccessLevelByName(name(id), 100)).isTrue();
            assertThat(character(id)).containsEntry("access_level", 100);
        }

        @Test
        void updateAccessLevelByName_shouldBeFalse_whenNameUnknown() {
            assertThat(repository.updateAccessLevelByName("nobody", 100)).isFalse();
        }
    }

    @Nested
    class UpdatePositionByName {
        @Test
        void updatePositionByName_shouldMoveCharacter() {
            final UUID id = insertCharacter();

            assertThat(repository.updatePositionByName(name(id), -84318, 244579, -3730))
                    .isTrue();
            assertThat(character(id))
                    .containsEntry("x", -84318)
                    .containsEntry("y", 244579)
                    .containsEntry("z", -3730);
        }

        @Test
        void updatePositionByName_shouldBeFalse_whenNameUnknown() {
            assertThat(repository.updatePositionByName("nobody", 0, 0, 0)).isFalse();
        }
    }

    @Nested
    class ClearClanPenaltyByName {
        @Test
        void clearClanJoinPenaltyByName_shouldClearOnlyJoinPenalty() {
            final UUID id = insertCharacter();
            setColumn(id, "clan_join_penalty_ends_at", JdbcTimes.utc(NOW));
            setColumn(id, "clan_create_penalty_ends_at", JdbcTimes.utc(NOW));

            assertThat(repository.clearClanJoinPenaltyByName(name(id))).isTrue();
            assertThat(character(id))
                    .containsEntry("clan_join_penalty_ends_at", null)
                    .containsEntry("clan_create_penalty_ends_at", JdbcTimes.utc(NOW));
        }

        @Test
        void clearClanCreatePenaltyByName_shouldClearOnlyCreatePenalty() {
            final UUID id = insertCharacter();
            setColumn(id, "clan_join_penalty_ends_at", JdbcTimes.utc(NOW));
            setColumn(id, "clan_create_penalty_ends_at", JdbcTimes.utc(NOW));

            assertThat(repository.clearClanCreatePenaltyByName(name(id))).isTrue();
            assertThat(character(id))
                    .containsEntry("clan_join_penalty_ends_at", JdbcTimes.utc(NOW))
                    .containsEntry("clan_create_penalty_ends_at", null);
        }
    }

    @Nested
    class UpdateClanPrivileges {
        @Test
        void updateClanPrivileges_shouldWriteMask() {
            final UUID id = insertCharacter();

            repository.updateClanPrivileges(registry.handleOf(id), 8388606);

            assertThat(character(id)).containsEntry("clan_privileges", 8388606);
        }
    }

    @Nested
    class RemoveFromClan {
        @Test
        void removeFromClan_shouldClearMembershipAndAcademyLinks() {
            final UUID clanId = insertClan();
            final UUID member = insertCharacter();
            final UUID apprentice = insertCharacter();
            final UUID sponsor = insertCharacter();
            for (UUID id : List.of(member, apprentice, sponsor)) {
                setColumn(id, "clan_id", clanId);
            }
            setColumn(member, "title", "Knight");
            setColumn(member, "clan_privileges", 16);
            setColumn(member, "pledge_type", -1);
            setColumn(member, "academy_join_level", 20);
            setColumn(member, "wants_peace", true);
            setColumn(member, "apprentice_id", apprentice);
            setColumn(member, "sponsor_id", sponsor);
            setColumn(apprentice, "sponsor_id", member);
            setColumn(sponsor, "apprentice_id", member);

            TRANSACTIONS.executeWithoutResult(
                    status -> repository.removeFromClan(registry.handleOf(member), NOW.plusSeconds(86400), null));

            assertThat(character(member))
                    .containsEntry("clan_id", null)
                    .containsEntry("title", null)
                    .containsEntry("clan_privileges", 0)
                    .containsEntry("pledge_type", 0)
                    .containsEntry("academy_join_level", null)
                    .containsEntry("wants_peace", false)
                    .containsEntry("apprentice_id", null)
                    .containsEntry("sponsor_id", null)
                    .containsEntry("clan_join_penalty_ends_at", JdbcTimes.utc(NOW.plusSeconds(86400)))
                    .containsEntry("clan_create_penalty_ends_at", null);
            assertThat(character(apprentice)).containsEntry("sponsor_id", null);
            assertThat(character(sponsor)).containsEntry("apprentice_id", null);
        }
    }

    @Nested
    class FindClanMembers {
        @Test
        void findClanMembers_shouldMapEveryMember() {
            final UUID clanId = insertClan();
            final UUID member = insertCharacter();
            final UUID apprentice = insertCharacter();
            setColumn(member, "clan_id", clanId);
            setColumn(member, "title", "Knight");
            setColumn(member, "power_grade", 4);
            setColumn(member, "pledge_type", 100);
            setColumn(member, "apprentice_id", apprentice);

            final List<CharacterClanMember> actual = repository.findClanMembers(registry.handleOf(clanId));

            assertThat(actual)
                    .containsExactly(CharacterClanMember.builder()
                            .objectId(registry.handleOf(member))
                            .name(name(member))
                            .level(1)
                            .classId(0)
                            .title("Knight")
                            .powerGrade(4)
                            .pledgeType(100)
                            .apprenticeId(registry.handleOf(apprentice))
                            .sponsorId(0)
                            .sex(Sex.MALE)
                            .race(Race.HUMAN)
                            .build());
        }
    }

    @Nested
    class UpdatePledgeType {
        @Test
        void updatePledgeType_shouldWritePledge() {
            final UUID id = insertCharacter();

            repository.updatePledgeType(registry.handleOf(id), -1);

            assertThat(character(id)).containsEntry("pledge_type", -1);
        }
    }

    @Nested
    class UpdatePowerGrade {
        @Test
        void updatePowerGrade_shouldWriteGrade() {
            final UUID id = insertCharacter();

            repository.updatePowerGrade(registry.handleOf(id), 6);

            assertThat(character(id)).containsEntry("power_grade", 6);
        }

        @Test
        void updatePowerGrade_shouldStoreNull_whenZero() {
            final UUID id = insertCharacter();
            setColumn(id, "power_grade", 6);

            repository.updatePowerGrade(registry.handleOf(id), 0);

            assertThat(character(id)).containsEntry("power_grade", null);
        }
    }

    @Nested
    class UpdateApprenticeAndSponsor {
        @Test
        void updateApprenticeAndSponsor_shouldStoreIdsAndNullForZero() {
            final UUID id = insertCharacter();
            final UUID apprentice = insertCharacter();

            repository.updateApprenticeAndSponsor(registry.handleOf(id), registry.handleOf(apprentice), 0);

            assertThat(character(id)).containsEntry("apprentice_id", apprentice).containsEntry("sponsor_id", null);
        }
    }

    @Nested
    class FindClanAndAlly {
        @Test
        void findClanAndAlly_shouldReturnClanAndAllianceHandles() {
            final UUID clanId = insertClan();
            JDBC.sql("UPDATE clans SET ally_clan_id = id, ally_name = 'Alliance' WHERE id = ?")
                    .param(clanId)
                    .update();
            final UUID id = insertCharacter();
            setColumn(id, "clan_id", clanId);

            final var actual = repository.findClanAndAlly(registry.handleOf(id));

            assertThat(actual).get().isEqualTo(clanAlly(registry.handleOf(clanId), registry.handleOf(clanId)));
        }

        @Test
        void findClanAndAlly_shouldReturnZeros_whenNoClan() {
            final UUID id = insertCharacter();

            assertThat(repository.findClanAndAlly(registry.handleOf(id))).get().isEqualTo(clanAlly(0, 0));
        }
    }

    @Nested
    class FindBirthdays {
        @ParameterizedTest(name = "created {0} h before now -> birthday")
        @ValueSource(longs = {8760 + 1, 8760 + 23})
        void findBirthdays_shouldReturnCharacter_whenAnniversaryInWindow(long hoursBeforeNow) {
            final UUID id = insertCharacter(EntityIds.next(), NOW.minus(Duration.ofHours(hoursBeforeNow)));

            final List<BirthdayCharacter> actual = repository.findBirthdays(NOW.minus(Duration.ofDays(1)), NOW);

            assertThat(actual).contains(birthday(registry.handleOf(id), name(id), 1));
        }

        @ParameterizedTest(name = "created {0} h before now -> no birthday")
        @ValueSource(longs = {8760 - 1, 8760 + 25, 23})
        void findBirthdays_shouldSkipCharacter_whenAnniversaryOutsideWindow(long hoursBeforeNow) {
            final UUID id = insertCharacter(EntityIds.next(), NOW.minus(Duration.ofHours(hoursBeforeNow)));

            final List<BirthdayCharacter> actual = repository.findBirthdays(NOW.minus(Duration.ofDays(1)), NOW);

            assertThat(actual).extracting(BirthdayCharacter::objectId).doesNotContain(registry.handleOf(id));
        }
    }

    private static CharacterRow newRow(int objectId, UUID accountId) {
        return CharacterRow.builder()
                .objectId(objectId)
                .accountId(accountId)
                .name("n" + EntityIds.next().toString().replace("-", "").substring(16))
                .title("")
                .titleColor(0xECF9A2)
                .race(Race.DARK_ELF)
                .sex(Sex.FEMALE)
                .face(1)
                .hairStyle(2)
                .hairColor(3)
                .classId(38)
                .baseClassId(38)
                .level(20)
                .exp(835863)
                .sp(1000)
                .maxHp(300)
                .currentHp(250)
                .maxCp(150)
                .currentCp(150)
                .maxMp(120)
                .currentMp(100)
                .x(28000)
                .y(11000)
                .z(-4200)
                .heading(500)
                .onlineStatus(OnlineStatus.ONLINE)
                .newbie(1)
                .language("")
                .vitalityPoints(20000)
                .createdAt(NOW)
                .lastAccessAt(NOW)
                .build();
    }

    private static UUID insertCharacter(UUID accountId, Instant createdAt) {
        final UUID id = insertCharacter();
        JDBC.sql("UPDATE characters SET account_id = ?, created_at = ? WHERE id = ?")
                .param(accountId)
                .param(JdbcTimes.utc(createdAt))
                .param(id)
                .update();
        return id;
    }

    private static void setColumn(UUID id, String column, Object value) {
        JDBC.sql("UPDATE characters SET " + column + " = ? WHERE id = ?")
                .param(value)
                .param(id)
                .update();
    }

    private static Map<String, Object> character(UUID id) {
        final Map<String, Object> row = JDBC.sql("SELECT * FROM characters WHERE id = ?")
                .param(id)
                .query()
                .singleRow();
        row.replaceAll(
                (column, value) -> value instanceof Timestamp timestamp ? JdbcTimes.utc(timestamp.toInstant()) : value);
        return row;
    }

    private static String name(UUID id) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();
    }

    private static int count(String table, UUID id) {
        final String column = "characters".equals(table) ? "id" : "character_id";
        return JDBC.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = ?")
                .param(id)
                .query(Integer.class)
                .single();
    }

    private static CharacterAccess access(int objectId, String name, int accessLevel) {
        return CharacterAccess.builder()
                .objectId(objectId)
                .name(name)
                .accessLevel(accessLevel)
                .build();
    }

    private static CharacterSummary summary(
            int objectId, String name, OnlineStatus onlineStatus, int classId, int level) {
        return CharacterSummary.builder()
                .objectId(objectId)
                .name(name)
                .onlineStatus(onlineStatus)
                .classId(classId)
                .level(level)
                .build();
    }

    private static CharacterClanAlly clanAlly(int clanId, int allyId) {
        return CharacterClanAlly.builder().clanId(clanId).allyId(allyId).build();
    }

    private static BirthdayCharacter birthday(int objectId, String name, int age) {
        return BirthdayCharacter.builder()
                .objectId(objectId)
                .name(name)
                .age(age)
                .build();
    }
}
