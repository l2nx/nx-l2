/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsFestivalScore;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SevenSignsFestivalScoreRepository {
    private static final String SELECT_ALL = """
        SELECT cycle, festival_id, cabal, scored_at, score, member_ids
        FROM seven_signs_festival_scores\
        """;
    private static final String UPSERT = """
        INSERT INTO seven_signs_festival_scores (cycle, festival_id, cabal, scored_at, score, member_ids)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (cycle, festival_id, cabal) DO UPDATE
        SET scored_at = EXCLUDED.scored_at, score = EXCLUDED.score, member_ids = EXCLUDED.member_ids\
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<SevenSignsFestivalScore> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> SevenSignsFestivalScore.builder()
                        .cycle(rs.getInt("cycle"))
                        .festivalId(rs.getInt("festival_id"))
                        .cabal(CabalColumns.toShortName(rs.getString("cabal")))
                        .scoredAt(JdbcTimes.instant(rs, "scored_at"))
                        .score(rs.getInt("score"))
                        .memberIds(memberIds(rs.getArray("member_ids")))
                        .build())
                .list();
    }

    public void saveAll(Collection<SevenSignsFestivalScore> scores) {
        jdbcTemplate.batchUpdate(UPSERT, scores, scores.size(), this::bind);
    }

    private void bind(PreparedStatement ps, SevenSignsFestivalScore score) throws SQLException {
        ps.setInt(1, score.cycle());
        ps.setInt(2, score.festivalId());
        ps.setString(3, CabalColumns.fromShortName(score.cabal()));
        if (score.scoredAt() == null) {
            ps.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(4, JdbcTimes.utc(score.scoredAt()));
        }
        ps.setInt(5, score.score());
        ps.setArray(
                6,
                ps.getConnection()
                        .createArrayOf(
                                "uuid",
                                score.memberIds().stream()
                                        .flatMap(memberId -> registry.findId(memberId).stream())
                                        .toArray()));
    }

    private List<Integer> memberIds(Array array) throws SQLException {
        return Arrays.stream((UUID[]) array.getArray()).map(registry::handleOf).toList();
    }
}
