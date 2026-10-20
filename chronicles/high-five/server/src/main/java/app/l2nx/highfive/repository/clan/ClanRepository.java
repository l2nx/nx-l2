/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan;

import static app.l2nx.highfive.repository.LegacyValues.handleOrZero;
import static app.l2nx.highfive.repository.LegacyValues.nullIfZero;
import static app.l2nx.highfive.repository.LegacyValues.textOrNull;
import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.clan.model.ClanUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanRepository {
    private static final String SELECT_IDS = "SELECT id FROM clans";
    private static final String SELECT_CLAN = """
        SELECT id, name, clan_level, reputation_score, blood_alliance_count, blood_oath_count,
               leader_character_id, new_leader_character_id, ally_clan_id, ally_name,
               crest_id, crest_large_id, ally_crest_id, bid_clan_hall_id,
               ally_penalty_ends_at, ally_penalty_type, character_penalty_ends_at, dissolving_ends_at
        FROM clans
        WHERE id = ?\
        """;
    private static final String SELECT_NAME_BY_CHARACTER_NAME = """
        SELECT c.name
        FROM clans c
        JOIN characters ch ON ch.clan_id = c.id
        WHERE ch.name = ?\
        """;
    private static final String INSERT_CLAN = """
        INSERT INTO clans (
            id, name, clan_level, reputation_score, blood_alliance_count, blood_oath_count,
            leader_character_id, new_leader_character_id, ally_clan_id, ally_name,
            crest_id, crest_large_id, ally_crest_id, bid_clan_hall_id,
            ally_penalty_ends_at, ally_penalty_type, character_penalty_ends_at, dissolving_ends_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String UPDATE_CLAN = """
        UPDATE clans
        SET leader_character_id = ?, new_leader_character_id = ?, ally_clan_id = ?, ally_name = ?,
            reputation_score = ?, ally_penalty_ends_at = ?, ally_penalty_type = ?,
            character_penalty_ends_at = ?, dissolving_ends_at = ?
        WHERE id = ?\
        """;
    private static final String UPDATE_BLOOD_ALLIANCE_COUNT = "UPDATE clans SET blood_alliance_count = ? WHERE id = ?";
    private static final String UPDATE_BLOOD_OATH_COUNT = "UPDATE clans SET blood_oath_count = ? WHERE id = ?";
    private static final String UPDATE_LEVEL = "UPDATE clans SET clan_level = ? WHERE id = ?";
    private static final String UPDATE_BID_CLAN_HALL = "UPDATE clans SET bid_clan_hall_id = ? WHERE id = ?";
    private static final String UPDATE_CREST = "UPDATE clans SET crest_id = ? WHERE id = ?";
    private static final String UPDATE_LARGE_CREST = "UPDATE clans SET crest_large_id = ? WHERE id = ?";
    private static final String UPDATE_ALLY_CREST = "UPDATE clans SET ally_crest_id = ? WHERE id = ?";
    private static final String UPDATE_ALLY_CREST_OF_ALLIANCE =
            "UPDATE clans SET ally_crest_id = ? WHERE ally_clan_id = ?";
    // ON DELETE SET NULL of ally_clan_id cannot clear the columns that only make sense with an alliance.
    private static final String CLEAR_ALLIANCE_LEFTOVERS = """
        UPDATE clans
        SET ally_name = NULL, ally_crest_id = NULL
        WHERE ally_clan_id IS NULL AND (ally_name IS NOT NULL OR ally_crest_id IS NOT NULL)\
        """;
    private static final String DELETE_CLAN = "DELETE FROM clans WHERE id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<Integer> findAllClanIds() {
        return jdbcClient
                .sql(SELECT_IDS)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("id", UUID.class)))
                .list();
    }

    public Optional<ClanRow> find(int clanId) {
        return registry.findId(clanId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_CLAN)
                        .param(id)
                        .query((rs, rowNum) -> toClan(rs))
                        .optional());
    }

    public Optional<String> findNameByCharacterName(String characterName) {
        return jdbcClient
                .sql(SELECT_NAME_BY_CHARACTER_NAME)
                .param(characterName)
                .query(String.class)
                .optional();
    }

    public void insert(ClanRow clan) {
        jdbcClient
                .sql(INSERT_CLAN)
                .param(registry.idOf(clan.clanId()))
                .param(clan.name())
                .param(clan.level())
                .param(clan.reputationScore())
                .param(clan.bloodAllianceCount())
                .param(clan.bloodOathCount())
                .param(References.required(registry, clan.leaderId()))
                .param(requiredOrNull(registry, clan.newLeaderId()))
                .param(requiredOrNull(registry, clan.allyId()))
                .param(textOrNull(clan.allyName()))
                .param(nullIfZero(clan.crestId()))
                .param(nullIfZero(clan.crestLargeId()))
                .param(nullIfZero(clan.allyCrestId()))
                .param(nullIfZero(clan.bidClanHallId()))
                .param(JdbcTimes.utc(clan.allyPenaltyEndsAt()))
                .param(allyPenaltyType(clan.allyPenaltyEndsAt(), clan.allyPenaltyType()))
                .param(JdbcTimes.utc(clan.characterPenaltyEndsAt()))
                .param(JdbcTimes.utc(clan.dissolvingEndsAt()))
                .update();
    }

    public void update(ClanUpdate clan) {
        registry.findId(clan.clanId())
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_CLAN)
                        .param(References.required(registry, clan.leaderId()))
                        .param(requiredOrNull(registry, clan.newLeaderId()))
                        .param(requiredOrNull(registry, clan.allyId()))
                        .param(textOrNull(clan.allyName()))
                        .param(clan.reputationScore())
                        .param(JdbcTimes.utc(clan.allyPenaltyEndsAt()))
                        .param(allyPenaltyType(clan.allyPenaltyEndsAt(), clan.allyPenaltyType()))
                        .param(JdbcTimes.utc(clan.characterPenaltyEndsAt()))
                        .param(JdbcTimes.utc(clan.dissolvingEndsAt()))
                        .param(id)
                        .update());
    }

    public void updateBloodAllianceCount(int clanId, int count) {
        updateColumn(UPDATE_BLOOD_ALLIANCE_COUNT, clanId, count);
    }

    public void updateBloodOathCount(int clanId, int count) {
        updateColumn(UPDATE_BLOOD_OATH_COUNT, clanId, count);
    }

    public void updateLevel(int clanId, int level) {
        updateColumn(UPDATE_LEVEL, clanId, level);
    }

    /**
     * The bid row in {@code clan_hall_auction_bids} must exist first: the clan column references it.
     *
     * @param clanHallId 0 when the clan bids nowhere
     */
    public void updateBidClanHall(int clanId, int clanHallId) {
        updateColumn(UPDATE_BID_CLAN_HALL, clanId, nullIfZero(clanHallId));
    }

    /**
     * @param crestId 0 removes the crest
     */
    public void updateCrest(int clanId, int crestId) {
        updateColumn(UPDATE_CREST, clanId, nullIfZero(crestId));
    }

    /**
     * @param crestId 0 removes the crest
     */
    public void updateLargeCrest(int clanId, int crestId) {
        updateColumn(UPDATE_LARGE_CREST, clanId, nullIfZero(crestId));
    }

    /**
     * @param crestId 0 removes the crest
     */
    public void updateAllyCrest(int clanId, int crestId) {
        updateColumn(UPDATE_ALLY_CREST, clanId, nullIfZero(crestId));
    }

    /**
     * @param crestId 0 removes the crest
     */
    public void updateAllyCrestOfAlliance(int allyId, int crestId) {
        updateColumn(UPDATE_ALLY_CREST_OF_ALLIANCE, allyId, nullIfZero(crestId));
    }

    /**
     * Clears the alliance name and crest of clans whose alliance leader clan was deleted.
     */
    public int clearAllianceLeftovers() {
        return jdbcClient.sql(CLEAR_ALLIANCE_LEFTOVERS).update();
    }

    /**
     * Deletes the clan with its notice, privileges, skills, sub-units and wars; members and residences lose it.
     */
    public void delete(int clanId) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient.sql(DELETE_CLAN).param(id).update());
    }

    private void updateColumn(String sql, int clanId, Integer value) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient.sql(sql).param(value).param(id).update());
    }

    private ClanRow toClan(ResultSet rs) throws SQLException {
        return ClanRow.builder()
                .clanId(registry.handleOf(rs.getObject("id", UUID.class)))
                .name(rs.getString("name"))
                .level(rs.getInt("clan_level"))
                .reputationScore(rs.getInt("reputation_score"))
                .bloodAllianceCount(rs.getInt("blood_alliance_count"))
                .bloodOathCount(rs.getInt("blood_oath_count"))
                .leaderId(registry.handleOf(rs.getObject("leader_character_id", UUID.class)))
                .newLeaderId(handleOrZero(registry, rs, "new_leader_character_id"))
                .allyId(handleOrZero(registry, rs, "ally_clan_id"))
                .allyName(rs.getString("ally_name"))
                .crestId(rs.getInt("crest_id"))
                .crestLargeId(rs.getInt("crest_large_id"))
                .allyCrestId(rs.getInt("ally_crest_id"))
                .bidClanHallId(rs.getInt("bid_clan_hall_id"))
                .allyPenaltyEndsAt(JdbcTimes.instant(rs, "ally_penalty_ends_at"))
                .allyPenaltyType(rs.getInt("ally_penalty_type"))
                .characterPenaltyEndsAt(JdbcTimes.instant(rs, "character_penalty_ends_at"))
                .dissolvingEndsAt(JdbcTimes.instant(rs, "dissolving_ends_at"))
                .build();
    }

    // The penalty type has no meaning without its end time; the schema keeps both NULL or both set.
    private static Integer allyPenaltyType(Instant endsAt, int type) {
        return endsAt == null ? null : nullIfZero(type);
    }
}
