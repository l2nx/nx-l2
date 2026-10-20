/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.olympiad.model.OlympiadNoble;
import app.l2nx.highfive.repository.olympiad.model.RankedNoble;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nobles of the running olympiad ({@code olympiad_nobles}) and their snapshot taken at the end of the last period
 * ({@code olympiad_nobles_eom}).
 */
@Repository
@RequiredArgsConstructor
public class OlympiadNobleRepository {
    private static final String SELECT_ALL = """
        SELECT n.character_id, c.name, n.class_id, n.olympiad_points, n.competitions_done, n.competitions_won,
               n.competitions_lost, n.competitions_drawn, n.competitions_done_week, n.competitions_done_week_classed,
               n.competitions_done_week_non_classed, n.competitions_done_week_team
        FROM olympiad_nobles n
        JOIN characters c ON c.id = n.character_id\
        """;
    private static final String UPSERT = """
        INSERT INTO olympiad_nobles (
            character_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost,
            competitions_drawn, competitions_done_week, competitions_done_week_classed,
            competitions_done_week_non_classed, competitions_done_week_team)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (character_id) DO UPDATE
        SET olympiad_points = EXCLUDED.olympiad_points, competitions_done = EXCLUDED.competitions_done,
            competitions_won = EXCLUDED.competitions_won, competitions_lost = EXCLUDED.competitions_lost,
            competitions_drawn = EXCLUDED.competitions_drawn,
            competitions_done_week = EXCLUDED.competitions_done_week,
            competitions_done_week_classed = EXCLUDED.competitions_done_week_classed,
            competitions_done_week_non_classed = EXCLUDED.competitions_done_week_non_classed,
            competitions_done_week_team = EXCLUDED.competitions_done_week_team\
        """;
    private static final String SELECT_HERO_CANDIDATE = """
        SELECT n.character_id, c.name
        FROM olympiad_nobles n
        JOIN characters c ON c.id = n.character_id
        WHERE n.class_id = ? AND n.competitions_done >= ? AND n.competitions_won > 0
        ORDER BY n.olympiad_points DESC, n.competitions_done DESC, n.competitions_won DESC
        LIMIT 1\
        """;
    private static final String SELECT_LEADER_NAMES = """
        SELECT c.name
        FROM olympiad_nobles n
        JOIN characters c ON c.id = n.character_id
        WHERE n.class_id = ANY (?) AND n.competitions_done >= ?
        ORDER BY n.olympiad_points DESC, n.competitions_done DESC, n.competitions_won DESC
        LIMIT ?\
        """;
    private static final String TRUNCATE = "TRUNCATE olympiad_nobles";
    private static final String TRUNCATE_SNAPSHOT = "TRUNCATE olympiad_nobles_eom";
    private static final String COPY_TO_SNAPSHOT = """
        INSERT INTO olympiad_nobles_eom (
            character_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost,
            competitions_drawn)
        SELECT character_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost,
               competitions_drawn
        FROM olympiad_nobles\
        """;
    private static final String SELECT_SNAPSHOT_RANKING = """
        SELECT character_id
        FROM olympiad_nobles_eom
        WHERE competitions_done >= ?
        ORDER BY olympiad_points DESC, competitions_done DESC, competitions_won DESC\
        """;
    private static final String SELECT_SNAPSHOT_LEADER_NAMES = """
        SELECT c.name
        FROM olympiad_nobles_eom n
        JOIN characters c ON c.id = n.character_id
        WHERE n.class_id = ANY (?) AND n.competitions_done >= ?
        ORDER BY n.olympiad_points DESC, n.competitions_done DESC, n.competitions_won DESC
        LIMIT ?\
        """;
    private static final String SELECT_SNAPSHOT_POINTS =
            "SELECT olympiad_points FROM olympiad_nobles_eom WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<OlympiadNoble> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> OlympiadNoble.builder()
                        .objectId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .characterName(rs.getString("name"))
                        .classId(rs.getInt("class_id"))
                        .olympiadPoints(rs.getInt("olympiad_points"))
                        .competitionsDone(rs.getInt("competitions_done"))
                        .competitionsWon(rs.getInt("competitions_won"))
                        .competitionsLost(rs.getInt("competitions_lost"))
                        .competitionsDrawn(rs.getInt("competitions_drawn"))
                        .competitionsDoneWeek(rs.getInt("competitions_done_week"))
                        .competitionsDoneWeekClassed(rs.getInt("competitions_done_week_classed"))
                        .competitionsDoneWeekNonClassed(rs.getInt("competitions_done_week_non_classed"))
                        .competitionsDoneWeekTeam(rs.getInt("competitions_done_week_team"))
                        .build())
                .list();
    }

    /**
     * Inserts new nobles and updates the counters of stored ones; the class of a stored noble is kept. A noble whose
     * character was deleted is skipped.
     */
    public void saveAll(Collection<OlympiadNoble> nobles) {
        jdbcTemplate.batchUpdate(
                UPSERT,
                nobles.stream()
                        .flatMap(noble -> registry.findId(noble.objectId()).stream()
                                .map(id -> new Object[] {
                                    id,
                                    noble.classId(),
                                    noble.olympiadPoints(),
                                    noble.competitionsDone(),
                                    noble.competitionsWon(),
                                    noble.competitionsLost(),
                                    noble.competitionsDrawn(),
                                    noble.competitionsDoneWeek(),
                                    noble.competitionsDoneWeekClassed(),
                                    noble.competitionsDoneWeekNonClassed(),
                                    noble.competitionsDoneWeekTeam()
                                }))
                        .toList());
    }

    /**
     * @return the best noble of the class with at least one win, the hero of that class
     */
    public Optional<RankedNoble> findHeroCandidate(int classId, int minCompetitions) {
        return jdbcClient
                .sql(SELECT_HERO_CANDIDATE)
                .param(classId)
                .param(minCompetitions)
                .query((rs, rowNum) -> RankedNoble.builder()
                        .objectId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .characterName(rs.getString("name"))
                        .build())
                .optional();
    }

    public List<String> findLeaderNames(Collection<Integer> classIds, int minCompetitions, int limit) {
        return findNames(SELECT_LEADER_NAMES, classIds, minCompetitions, limit);
    }

    public void deleteAll() {
        jdbcClient.sql(TRUNCATE).update();
    }

    @Transactional
    public void replaceSnapshot() {
        jdbcClient.sql(TRUNCATE_SNAPSHOT).update();
        jdbcClient.sql(COPY_TO_SNAPSHOT).update();
    }

    public List<Integer> findSnapshotRanking(int minCompetitions) {
        return jdbcClient
                .sql(SELECT_SNAPSHOT_RANKING)
                .param(minCompetitions)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("character_id", UUID.class)))
                .list();
    }

    public List<String> findSnapshotLeaderNames(Collection<Integer> classIds, int minCompetitions, int limit) {
        return findNames(SELECT_SNAPSHOT_LEADER_NAMES, classIds, minCompetitions, limit);
    }

    /**
     * @return the points of the character at the end of the last period, empty when it was no noble then
     */
    public OptionalInt findSnapshotPoints(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_SNAPSHOT_POINTS)
                        .param(id)
                        .query(Integer.class)
                        .optional())
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    private List<String> findNames(String sql, Collection<Integer> classIds, int minCompetitions, int limit) {
        return jdbcClient
                .sql(sql)
                .param(classIds.toArray(Integer[]::new))
                .param(minCompetitions)
                .param(limit)
                .query(String.class)
                .list();
    }
}
