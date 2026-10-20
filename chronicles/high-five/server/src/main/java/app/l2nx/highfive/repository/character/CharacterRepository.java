/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import static app.l2nx.highfive.repository.LegacyValues.handleOrZero;
import static app.l2nx.highfive.repository.LegacyValues.positiveOrNull;
import static app.l2nx.highfive.repository.LegacyValues.textOrNull;
import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.repository.character.model.BirthdayCharacter;
import app.l2nx.highfive.repository.character.model.CharacterAccess;
import app.l2nx.highfive.repository.character.model.CharacterClanAlly;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterSummary;
import app.l2nx.highfive.repository.character.model.Faction;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CharacterRepository {
    private static final String COLUMNS = """
        id, account_id, name, title, title_color, race, sex, face, hair_style, hair_color, class_id, base_class_id,
        character_level, exp, exp_before_death, sp, max_hp, current_hp, max_cp, current_cp, max_mp, current_mp,
        x, y, z, heading, karma, fame, pvp_kills, pk_kills, access_level, online_status, online_time_s, newbie_flags,
        noble, in_seven_signs_dungeon, faction, language, vitality_points, death_penalty_level, bookmark_slots,
        pc_cafe_points, clan_id, clan_privileges, pledge_type, power_grade, academy_join_level, apprentice_id,
        sponsor_id, wants_peace, clan_join_penalty_ends_at, clan_create_penalty_ends_at, delete_at, created_at,
        last_access_at\
        """;
    private static final String INSERT_CHARACTER = """
        INSERT INTO characters (%s)
        VALUES (
            :id, :accountId, :name, :title, :titleColor, :race, :sex, :face, :hairStyle, :hairColor, :classId,
            :baseClassId, :level, :exp, :expBeforeDeath, :sp, :maxHp, :currentHp, :maxCp, :currentCp, :maxMp,
            :currentMp, :x, :y, :z, :heading, :karma, :fame, :pvpKills, :pkKills, :accessLevel, :onlineStatus,
            :onlineTime, :newbie, :noble, :inSevenSignsDungeon, :faction, :language, :vitalityPoints,
            :deathPenaltyLevel, :bookmarkSlots, :pcCafePoints, :clanId, :clanPrivileges, :pledgeType, :powerGrade,
            :academyJoinLevel, :apprenticeId, :sponsorId, :wantsPeace, :clanJoinPenaltyEndsAt,
            :clanCreatePenaltyEndsAt, :deleteAt, :createdAt, :lastAccessAt)\
        """.formatted(COLUMNS);
    // The periodic save leaves the account, the creation time and the last access alone.
    private static final String UPDATE_CHARACTER = """
        UPDATE characters
        SET name = :name, title = :title, title_color = :titleColor, race = :race, sex = :sex, face = :face,
            hair_style = :hairStyle, hair_color = :hairColor, class_id = :classId, base_class_id = :baseClassId,
            character_level = :level, exp = :exp, exp_before_death = :expBeforeDeath, sp = :sp, max_hp = :maxHp,
            current_hp = :currentHp, max_cp = :maxCp, current_cp = :currentCp, max_mp = :maxMp,
            current_mp = :currentMp, x = :x, y = :y, z = :z, heading = :heading, karma = :karma, fame = :fame,
            pvp_kills = :pvpKills, pk_kills = :pkKills, access_level = :accessLevel, online_status = :onlineStatus,
            online_time_s = :onlineTime, newbie_flags = :newbie, noble = :noble,
            in_seven_signs_dungeon = :inSevenSignsDungeon, faction = :faction, language = :language,
            vitality_points = :vitalityPoints, death_penalty_level = :deathPenaltyLevel,
            bookmark_slots = :bookmarkSlots, pc_cafe_points = :pcCafePoints, clan_id = :clanId,
            clan_privileges = :clanPrivileges, pledge_type = :pledgeType, power_grade = :powerGrade,
            academy_join_level = :academyJoinLevel, apprentice_id = :apprenticeId, sponsor_id = :sponsorId,
            wants_peace = :wantsPeace, clan_join_penalty_ends_at = :clanJoinPenaltyEndsAt,
            clan_create_penalty_ends_at = :clanCreatePenaltyEndsAt, delete_at = :deleteAt
        WHERE id = :id\
        """;
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM characters WHERE id = ?";
    private static final String SELECT_BY_ACCOUNT =
            "SELECT " + COLUMNS + " FROM characters WHERE account_id = ? ORDER BY created_at";
    private static final String SELECT_OTHER_NAMES =
            "SELECT id, name FROM characters WHERE account_id = ? AND id <> ? ORDER BY created_at";
    private static final String UPDATE_ONLINE_STATUS =
            "UPDATE characters SET online_status = ?, last_access_at = ? WHERE id = ?";
    private static final String RESET_ONLINE_STATUS =
            "UPDATE characters SET online_status = 'OFFLINE' WHERE online_status <> 'OFFLINE'";
    private static final String SELECT_ALL_ACCESS = "SELECT id, name, access_level FROM characters";
    private static final String SELECT_ACCESS_BY_NAME = "SELECT id, name, access_level FROM characters WHERE name = ?";
    private static final String SELECT_ACCESS_BY_ID = "SELECT id, name, access_level FROM characters WHERE id = ?";
    private static final String EXISTS_BY_NAME = "SELECT EXISTS (SELECT 1 FROM characters WHERE name = ?)";
    private static final String COUNT_BY_ACCOUNT = "SELECT count(*) FROM characters WHERE account_id = ?";
    private static final String SELECT_DELETE_TIMES =
            "SELECT delete_at FROM characters WHERE account_id = ? AND delete_at IS NOT NULL";
    private static final String SELECT_SUMMARY =
            "SELECT id, name, online_status, class_id, character_level FROM characters WHERE id = ?";
    private static final String SELECT_CLAN_ID = "SELECT clan_id FROM characters WHERE id = ?";
    private static final String SELECT_ACCOUNT_ID = "SELECT account_id FROM characters WHERE id = ?";
    private static final String UPDATE_DELETE_AT = "UPDATE characters SET delete_at = ? WHERE id = ?";
    private static final String DELETE_CHARACTER = "DELETE FROM characters WHERE id = ?";
    private static final String UPDATE_KARMA = "UPDATE characters SET karma = ?, pk_kills = ? WHERE id = ?";
    private static final String UPDATE_ACCESS_LEVEL = "UPDATE characters SET access_level = ? WHERE id = ?";
    private static final String UPDATE_ACCESS_LEVEL_BY_NAME = "UPDATE characters SET access_level = ? WHERE name = ?";
    private static final String UPDATE_POSITION_BY_NAME = "UPDATE characters SET x = ?, y = ?, z = ? WHERE name = ?";
    private static final String CLEAR_CLAN_JOIN_PENALTY_BY_NAME =
            "UPDATE characters SET clan_join_penalty_ends_at = NULL WHERE name = ?";
    private static final String CLEAR_CLAN_CREATE_PENALTY_BY_NAME =
            "UPDATE characters SET clan_create_penalty_ends_at = NULL WHERE name = ?";
    private static final String UPDATE_CLAN_PRIVILEGES = "UPDATE characters SET clan_privileges = ? WHERE id = ?";
    private static final String REMOVE_FROM_CLAN = """
        UPDATE characters
        SET clan_id = NULL, title = NULL, clan_join_penalty_ends_at = ?, clan_create_penalty_ends_at = ?,
            clan_privileges = 0, wants_peace = false, pledge_type = 0, academy_join_level = NULL,
            apprentice_id = NULL, sponsor_id = NULL
        WHERE id = ?\
        """;
    private static final String CLEAR_APPRENTICE = "UPDATE characters SET apprentice_id = NULL WHERE apprentice_id = ?";
    private static final String CLEAR_SPONSOR = "UPDATE characters SET sponsor_id = NULL WHERE sponsor_id = ?";
    private static final String SELECT_CLAN_MEMBERS = """
        SELECT id, name, character_level, class_id, title, power_grade, pledge_type, apprentice_id, sponsor_id, sex,
               race
        FROM characters
        WHERE clan_id = ?\
        """;
    private static final String UPDATE_PLEDGE_TYPE = "UPDATE characters SET pledge_type = ? WHERE id = ?";
    private static final String UPDATE_POWER_GRADE = "UPDATE characters SET power_grade = ? WHERE id = ?";
    private static final String UPDATE_APPRENTICE_AND_SPONSOR =
            "UPDATE characters SET apprentice_id = ?, sponsor_id = ? WHERE id = ?";
    private static final String SELECT_CLAN_AND_ALLY = """
        SELECT c.clan_id, k.ally_clan_id
        FROM characters c
        LEFT JOIN clans k ON k.id = c.clan_id
        WHERE c.id = ?\
        """;
    // Latest anniversary of the creation not in the future; age() follows the session time zone.
    private static final String SELECT_BIRTHDAYS = """
        SELECT id, name, age
        FROM (SELECT id, name, created_at, extract(year FROM age(?, created_at))::integer AS age FROM characters) c
        WHERE age > 0 AND created_at + make_interval(years => age) >= ?\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public void insert(CharacterRow row) {
        bind(jdbcClient.sql(INSERT_CHARACTER), row)
                .param("accountId", row.accountId())
                .param("createdAt", JdbcTimes.utc(row.createdAt()))
                .param("lastAccessAt", JdbcTimes.utc(row.lastAccessAt()))
                .update();
    }

    public void update(CharacterRow row) {
        bind(jdbcClient.sql(UPDATE_CHARACTER), row).update();
    }

    public Optional<CharacterRow> findById(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_BY_ID)
                        .param(id)
                        .query(this::mapRow)
                        .optional());
    }

    /**
     * @return the characters of the account, oldest first
     */
    public List<CharacterRow> findByAccount(UUID accountId) {
        return jdbcClient
                .sql(SELECT_BY_ACCOUNT)
                .param(accountId)
                .query(this::mapRow)
                .list();
    }

    /**
     * @return names by handle of the other characters of the account, oldest first
     */
    public Map<Integer, String> findOtherNames(UUID accountId, int excludedObjectId) {
        final Map<Integer, String> names = new LinkedHashMap<>();
        jdbcClient
                .sql(SELECT_OTHER_NAMES)
                .param(accountId)
                .param(registry.idOf(excludedObjectId))
                .query(rs -> {
                    names.put(registry.handleOf(rs.getObject("id", UUID.class)), rs.getString("name"));
                });
        return names;
    }

    public void updateOnlineStatus(int objectId, OnlineStatus status, Instant lastAccessAt) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_ONLINE_STATUS)
                        .param(status.name())
                        .param(JdbcTimes.utc(lastAccessAt))
                        .param(id)
                        .update());
    }

    /**
     * Marks every character offline: a start after a crash finds the status of the last run.
     */
    public void resetOnlineStatus() {
        jdbcClient.sql(RESET_ONLINE_STATUS).update();
    }

    public List<CharacterAccess> findAllAccess() {
        return jdbcClient.sql(SELECT_ALL_ACCESS).query(this::mapAccess).list();
    }

    public Optional<CharacterAccess> findAccessByName(String name) {
        return jdbcClient
                .sql(SELECT_ACCESS_BY_NAME)
                .param(name)
                .query(this::mapAccess)
                .optional();
    }

    public Optional<CharacterAccess> findAccessById(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_ACCESS_BY_ID)
                        .param(id)
                        .query(this::mapAccess)
                        .optional());
    }

    public boolean existsByName(String name) {
        return jdbcClient.sql(EXISTS_BY_NAME).param(name).query(Boolean.class).single();
    }

    public int countByAccount(UUID accountId) {
        return jdbcClient
                .sql(COUNT_BY_ACCOUNT)
                .param(accountId)
                .query(Integer.class)
                .single();
    }

    public List<Instant> findDeleteTimes(UUID accountId) {
        return jdbcClient
                .sql(SELECT_DELETE_TIMES)
                .param(accountId)
                .query((rs, rowNum) -> JdbcTimes.instant(rs, "delete_at"))
                .list();
    }

    public Optional<CharacterSummary> findSummary(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_SUMMARY)
                        .param(id)
                        .query((rs, rowNum) -> CharacterSummary.builder()
                                .objectId(objectId)
                                .name(rs.getString("name"))
                                .onlineStatus(OnlineStatus.valueOf(rs.getString("online_status")))
                                .classId(rs.getInt("class_id"))
                                .level(rs.getInt("character_level"))
                                .build())
                        .optional());
    }

    /**
     * @return the clan handle, {@code 0} for a character without a clan or a missing character
     */
    public int findClanId(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_CLAN_ID)
                        .param(id)
                        .query((rs, rowNum) -> handleOrZero(registry, rs.getObject("clan_id", UUID.class)))
                        .optional())
                .orElse(0);
    }

    public Optional<UUID> findAccountId(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_ACCOUNT_ID)
                        .param(id)
                        .query(UUID.class)
                        .optional());
    }

    /**
     * @param deleteAt when the character is deleted, {@code null} to cancel a pending deletion
     */
    public void updateDeleteTime(int objectId, Instant deleteAt) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_DELETE_AT)
                        .param(JdbcTimes.utc(deleteAt))
                        .param(id)
                        .update());
    }

    /**
     * Deletes the character with every row that belongs to it: the child tables cascade.
     */
    public void delete(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_CHARACTER).param(id).update());
    }

    public boolean updateKarmaAndPkKills(int objectId, int karma, int pkKills) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                                .sql(UPDATE_KARMA)
                                .param(karma)
                                .param(pkKills)
                                .param(id)
                                .update()
                        > 0)
                .orElse(false);
    }

    public void updateAccessLevel(int objectId, int accessLevel) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_ACCESS_LEVEL)
                        .param(accessLevel)
                        .param(id)
                        .update());
    }

    public boolean updateAccessLevelByName(String name, int accessLevel) {
        return jdbcClient
                        .sql(UPDATE_ACCESS_LEVEL_BY_NAME)
                        .param(accessLevel)
                        .param(name)
                        .update()
                > 0;
    }

    public boolean updatePositionByName(String name, int x, int y, int z) {
        return jdbcClient
                        .sql(UPDATE_POSITION_BY_NAME)
                        .param(x)
                        .param(y)
                        .param(z)
                        .param(name)
                        .update()
                > 0;
    }

    public boolean clearClanJoinPenaltyByName(String name) {
        return jdbcClient.sql(CLEAR_CLAN_JOIN_PENALTY_BY_NAME).param(name).update() > 0;
    }

    public boolean clearClanCreatePenaltyByName(String name) {
        return jdbcClient.sql(CLEAR_CLAN_CREATE_PENALTY_BY_NAME).param(name).update() > 0;
    }

    public void updateClanPrivileges(int objectId, int privileges) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_CLAN_PRIVILEGES)
                        .param(privileges)
                        .param(id)
                        .update());
    }

    /**
     * Clears the clan membership of a character who leaves or is dismissed, and its academy links: its own and those
     * of the members it sponsored or was sponsored by.
     *
     * @param joinPenaltyEndsAt {@code null} for no penalty
     * @param createPenaltyEndsAt {@code null} for no penalty
     */
    @Transactional
    public void removeFromClan(int objectId, Instant joinPenaltyEndsAt, Instant createPenaltyEndsAt) {
        registry.findId(objectId).ifPresent(id -> {
            jdbcClient
                    .sql(REMOVE_FROM_CLAN)
                    .param(JdbcTimes.utc(joinPenaltyEndsAt))
                    .param(JdbcTimes.utc(createPenaltyEndsAt))
                    .param(id)
                    .update();
            jdbcClient.sql(CLEAR_APPRENTICE).param(id).update();
            jdbcClient.sql(CLEAR_SPONSOR).param(id).update();
        });
    }

    public List<CharacterClanMember> findClanMembers(int clanId) {
        return registry.findId(clanId)
                .map(refId -> jdbcClient
                        .sql(SELECT_CLAN_MEMBERS)
                        .param(refId)
                        .query((rs, rowNum) -> CharacterClanMember.builder()
                                .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                                .name(rs.getString("name"))
                                .level(rs.getInt("character_level"))
                                .classId(rs.getInt("class_id"))
                                .title(rs.getString("title"))
                                .powerGrade(rs.getInt("power_grade"))
                                .pledgeType(rs.getInt("pledge_type"))
                                .apprenticeId(handleOrZero(registry, rs.getObject("apprentice_id", UUID.class)))
                                .sponsorId(handleOrZero(registry, rs.getObject("sponsor_id", UUID.class)))
                                .sex(Sex.valueOf(rs.getString("sex")))
                                .race(Race.valueOf(rs.getString("race")))
                                .build())
                        .list())
                .orElse(List.of());
    }

    public void updatePledgeType(int objectId, int pledgeType) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_PLEDGE_TYPE)
                        .param(pledgeType)
                        .param(id)
                        .update());
    }

    /**
     * @param powerGrade {@code 0} to clear it
     */
    public void updatePowerGrade(int objectId, int powerGrade) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_POWER_GRADE)
                        .param(positiveOrNull(powerGrade))
                        .param(id)
                        .update());
    }

    /**
     * @param apprenticeId the apprentice handle, {@code 0} for none
     * @param sponsorId the sponsor handle, {@code 0} for none
     */
    public void updateApprenticeAndSponsor(int objectId, int apprenticeId, int sponsorId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_APPRENTICE_AND_SPONSOR)
                        .param(requiredOrNull(registry, apprenticeId))
                        .param(requiredOrNull(registry, sponsorId))
                        .param(id)
                        .update());
    }

    public Optional<CharacterClanAlly> findClanAndAlly(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_CLAN_AND_ALLY)
                        .param(id)
                        .query((rs, rowNum) -> CharacterClanAlly.builder()
                                .clanId(handleOrZero(registry, rs.getObject("clan_id", UUID.class)))
                                .allyId(handleOrZero(registry, rs.getObject("ally_clan_id", UUID.class)))
                                .build())
                        .optional());
    }

    /**
     * @return the characters whose creation anniversary fell between {@code since} and {@code now}, at least one year
     *     after the creation
     */
    public List<BirthdayCharacter> findBirthdays(Instant since, Instant now) {
        return jdbcClient
                .sql(SELECT_BIRTHDAYS)
                .param(JdbcTimes.utc(now))
                .param(JdbcTimes.utc(since))
                .query((rs, rowNum) -> BirthdayCharacter.builder()
                        .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                        .name(rs.getString("name"))
                        .age(rs.getInt("age"))
                        .build())
                .list();
    }

    private StatementSpec bind(StatementSpec statement, CharacterRow row) {
        return statement
                .param("id", registry.idOf(row.objectId()))
                .param("name", row.name())
                .param("title", textOrNull(row.title()))
                .param("titleColor", row.titleColor())
                .param("race", row.race().name())
                .param("sex", row.sex().name())
                .param("face", row.face())
                .param("hairStyle", row.hairStyle())
                .param("hairColor", row.hairColor())
                .param("classId", row.classId())
                .param("baseClassId", row.baseClassId())
                .param("level", row.level())
                .param("exp", row.exp())
                .param("expBeforeDeath", row.expBeforeDeath())
                .param("sp", row.sp())
                .param("maxHp", row.maxHp())
                .param("currentHp", row.currentHp())
                .param("maxCp", row.maxCp())
                .param("currentCp", row.currentCp())
                .param("maxMp", row.maxMp())
                .param("currentMp", row.currentMp())
                .param("x", row.x())
                .param("y", row.y())
                .param("z", row.z())
                .param("heading", row.heading())
                .param("karma", row.karma())
                .param("fame", row.fame())
                .param("pvpKills", row.pvpKills())
                .param("pkKills", row.pkKills())
                .param("accessLevel", row.accessLevel())
                .param("onlineStatus", row.onlineStatus().name())
                .param("onlineTime", row.onlineTimeSeconds())
                .param("newbie", row.newbie())
                .param("noble", row.noble())
                .param("inSevenSignsDungeon", row.inSevenSignsDungeon())
                .param("faction", row.faction() == null ? null : row.faction().name())
                .param("language", textOrNull(row.language()))
                .param("vitalityPoints", row.vitalityPoints())
                .param("deathPenaltyLevel", row.deathPenaltyLevel())
                .param("bookmarkSlots", row.bookmarkSlots())
                .param("pcCafePoints", row.pcCafePoints())
                .param("clanId", requiredOrNull(registry, row.clanId()))
                .param("clanPrivileges", row.clanPrivileges())
                .param("pledgeType", row.pledgeType())
                .param("powerGrade", positiveOrNull(row.powerGrade()))
                .param("academyJoinLevel", positiveOrNull(row.academyJoinLevel()))
                .param("apprenticeId", requiredOrNull(registry, row.apprenticeId()))
                .param("sponsorId", requiredOrNull(registry, row.sponsorId()))
                .param("wantsPeace", row.wantsPeace())
                .param("clanJoinPenaltyEndsAt", JdbcTimes.utc(row.clanJoinPenaltyEndsAt()))
                .param("clanCreatePenaltyEndsAt", JdbcTimes.utc(row.clanCreatePenaltyEndsAt()))
                .param("deleteAt", JdbcTimes.utc(row.deleteAt()));
    }

    private CharacterRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        final String faction = rs.getString("faction");
        return CharacterRow.builder()
                .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                .accountId(rs.getObject("account_id", UUID.class))
                .name(rs.getString("name"))
                .title(rs.getString("title"))
                .titleColor(rs.getInt("title_color"))
                .race(Race.valueOf(rs.getString("race")))
                .sex(Sex.valueOf(rs.getString("sex")))
                .face(rs.getInt("face"))
                .hairStyle(rs.getInt("hair_style"))
                .hairColor(rs.getInt("hair_color"))
                .classId(rs.getInt("class_id"))
                .baseClassId(rs.getInt("base_class_id"))
                .level(rs.getInt("character_level"))
                .exp(rs.getLong("exp"))
                .expBeforeDeath(rs.getLong("exp_before_death"))
                .sp(rs.getLong("sp"))
                .maxHp(rs.getInt("max_hp"))
                .currentHp(rs.getDouble("current_hp"))
                .maxCp(rs.getInt("max_cp"))
                .currentCp(rs.getDouble("current_cp"))
                .maxMp(rs.getInt("max_mp"))
                .currentMp(rs.getDouble("current_mp"))
                .x(rs.getInt("x"))
                .y(rs.getInt("y"))
                .z(rs.getInt("z"))
                .heading(rs.getInt("heading"))
                .karma(rs.getInt("karma"))
                .fame(rs.getInt("fame"))
                .pvpKills(rs.getInt("pvp_kills"))
                .pkKills(rs.getInt("pk_kills"))
                .accessLevel(rs.getInt("access_level"))
                .onlineStatus(OnlineStatus.valueOf(rs.getString("online_status")))
                .onlineTimeSeconds(rs.getLong("online_time_s"))
                .newbie(rs.getInt("newbie_flags"))
                .noble(rs.getBoolean("noble"))
                .inSevenSignsDungeon(rs.getBoolean("in_seven_signs_dungeon"))
                .faction(faction == null ? null : Faction.valueOf(faction))
                .language(rs.getString("language"))
                .vitalityPoints(rs.getInt("vitality_points"))
                .deathPenaltyLevel(rs.getInt("death_penalty_level"))
                .bookmarkSlots(rs.getInt("bookmark_slots"))
                .pcCafePoints(rs.getInt("pc_cafe_points"))
                .clanId(handleOrZero(registry, rs.getObject("clan_id", UUID.class)))
                .clanPrivileges(rs.getInt("clan_privileges"))
                .pledgeType(rs.getInt("pledge_type"))
                .powerGrade(rs.getInt("power_grade"))
                .academyJoinLevel(rs.getInt("academy_join_level"))
                .apprenticeId(handleOrZero(registry, rs.getObject("apprentice_id", UUID.class)))
                .sponsorId(handleOrZero(registry, rs.getObject("sponsor_id", UUID.class)))
                .wantsPeace(rs.getBoolean("wants_peace"))
                .clanJoinPenaltyEndsAt(JdbcTimes.instant(rs, "clan_join_penalty_ends_at"))
                .clanCreatePenaltyEndsAt(JdbcTimes.instant(rs, "clan_create_penalty_ends_at"))
                .deleteAt(JdbcTimes.instant(rs, "delete_at"))
                .createdAt(JdbcTimes.instant(rs, "created_at"))
                .lastAccessAt(JdbcTimes.instant(rs, "last_access_at"))
                .build();
    }

    private CharacterAccess mapAccess(ResultSet rs, int rowNum) throws SQLException {
        return CharacterAccess.builder()
                .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                .name(rs.getString("name"))
                .accessLevel(rs.getInt("access_level"))
                .build();
    }
}
