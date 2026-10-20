/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SevenSignsStatusRepository {
    // Index = the SevenSigns.PERIOD_* constant.
    private static final List<String> PERIODS =
            List.of("COMP_RECRUITING", "COMPETITION", "COMP_RESULTS", "SEAL_VALIDATION");

    private static final String SELECT = """
        SELECT current_cycle, festival_cycle, active_period, saved_at, previous_winner, dawn_stone_score,
               dawn_festival_score, dusk_stone_score, dusk_festival_score, avarice_owner, gnosis_owner, strife_owner,
               avarice_dawn_score, gnosis_dawn_score, strife_dawn_score, avarice_dusk_score, gnosis_dusk_score,
               strife_dusk_score, accumulated_bonus_0, accumulated_bonus_1, accumulated_bonus_2, accumulated_bonus_3,
               accumulated_bonus_4
        FROM seven_signs_status
        WHERE id = 0\
        """;
    private static final String UPDATE = """
        UPDATE seven_signs_status
        SET current_cycle = ?, festival_cycle = ?, active_period = ?, saved_at = ?, previous_winner = ?,
            dawn_stone_score = ?, dawn_festival_score = ?, dusk_stone_score = ?, dusk_festival_score = ?,
            avarice_owner = ?, gnosis_owner = ?, strife_owner = ?,
            avarice_dawn_score = ?, gnosis_dawn_score = ?, strife_dawn_score = ?,
            avarice_dusk_score = ?, gnosis_dusk_score = ?, strife_dusk_score = ?,
            accumulated_bonus_0 = ?, accumulated_bonus_1 = ?, accumulated_bonus_2 = ?, accumulated_bonus_3 = ?,
            accumulated_bonus_4 = ?
        WHERE id = 0\
        """;
    private static final int FESTIVAL_COUNT = 5;

    private final JdbcClient jdbcClient;

    public SevenSignsStatus find() {
        return jdbcClient
                .sql(SELECT)
                .query((rs, rowNum) -> SevenSignsStatus.builder()
                        .currentCycle(rs.getInt("current_cycle"))
                        .festivalCycle(rs.getInt("festival_cycle"))
                        .activePeriod(PERIODS.indexOf(rs.getString("active_period")))
                        .savedAt(JdbcTimes.instant(rs, "saved_at"))
                        .previousWinner(CabalColumns.toId(rs.getString("previous_winner")))
                        .dawnStoneScore(rs.getLong("dawn_stone_score"))
                        .dawnFestivalScore(rs.getInt("dawn_festival_score"))
                        .duskStoneScore(rs.getLong("dusk_stone_score"))
                        .duskFestivalScore(rs.getInt("dusk_festival_score"))
                        .avariceOwner(CabalColumns.toId(rs.getString("avarice_owner")))
                        .gnosisOwner(CabalColumns.toId(rs.getString("gnosis_owner")))
                        .strifeOwner(CabalColumns.toId(rs.getString("strife_owner")))
                        .avariceDawnScore(rs.getInt("avarice_dawn_score"))
                        .gnosisDawnScore(rs.getInt("gnosis_dawn_score"))
                        .strifeDawnScore(rs.getInt("strife_dawn_score"))
                        .avariceDuskScore(rs.getInt("avarice_dusk_score"))
                        .gnosisDuskScore(rs.getInt("gnosis_dusk_score"))
                        .strifeDuskScore(rs.getInt("strife_dusk_score"))
                        .accumulatedBonuses(List.of(
                                rs.getInt("accumulated_bonus_0"),
                                rs.getInt("accumulated_bonus_1"),
                                rs.getInt("accumulated_bonus_2"),
                                rs.getInt("accumulated_bonus_3"),
                                rs.getInt("accumulated_bonus_4")))
                        .build())
                .single();
    }

    public void update(SevenSignsStatus status) {
        final List<Integer> bonuses = status.accumulatedBonuses();
        if (bonuses.size() != FESTIVAL_COUNT) {
            throw new IllegalArgumentException("Expected " + FESTIVAL_COUNT + " festival bonuses, got " + bonuses);
        }
        jdbcClient
                .sql(UPDATE)
                .param(status.currentCycle())
                .param(status.festivalCycle())
                .param(PERIODS.get(status.activePeriod()))
                .param(JdbcTimes.utc(status.savedAt()))
                .param(CabalColumns.fromId(status.previousWinner()))
                .param(Math.round(status.dawnStoneScore()))
                .param(status.dawnFestivalScore())
                .param(Math.round(status.duskStoneScore()))
                .param(status.duskFestivalScore())
                .param(CabalColumns.fromId(status.avariceOwner()))
                .param(CabalColumns.fromId(status.gnosisOwner()))
                .param(CabalColumns.fromId(status.strifeOwner()))
                .param(status.avariceDawnScore())
                .param(status.gnosisDawnScore())
                .param(status.strifeDawnScore())
                .param(status.avariceDuskScore())
                .param(status.gnosisDuskScore())
                .param(status.strifeDuskScore())
                .param(bonuses.get(0))
                .param(bonuses.get(1))
                .param(bonuses.get(2))
                .param(bonuses.get(3))
                .param(bonuses.get(4))
                .update();
    }
}
