/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.minigame;

import app.l2nx.highfive.repository.minigame.model.MonsterRaceResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MonsterRaceRepository {
    private static final String SELECT_RESULTS = """
        SELECT race_number, winner_index, runner_up_index, winner_odds
        FROM monster_race_results
        ORDER BY race_number\
        """;
    private static final String UPSERT_RESULT = """
        INSERT INTO monster_race_results (race_number, winner_index, runner_up_index, winner_odds)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (race_number) DO UPDATE
        SET winner_index = EXCLUDED.winner_index, runner_up_index = EXCLUDED.runner_up_index,
            winner_odds = EXCLUDED.winner_odds\
        """;
    private static final String SELECT_BETS = "SELECT lane, bet_total FROM monster_race_bets ORDER BY lane";
    private static final String UPSERT_BET = """
        INSERT INTO monster_race_bets (lane, bet_total)
        VALUES (?, ?)
        ON CONFLICT (lane) DO UPDATE SET bet_total = EXCLUDED.bet_total\
        """;
    private static final String CLEAR_BETS = "UPDATE monster_race_bets SET bet_total = 0";

    private final JdbcClient jdbcClient;

    /**
     * @return every result in race order: the history is indexed by race number
     */
    public List<MonsterRaceResult> findResults() {
        return jdbcClient
                .sql(SELECT_RESULTS)
                .query((rs, rowNum) -> MonsterRaceResult.builder()
                        .raceId(rs.getInt("race_number"))
                        .first(rs.getInt("winner_index"))
                        .second(rs.getInt("runner_up_index"))
                        .oddRate(rs.getDouble("winner_odds"))
                        .build())
                .list();
    }

    public void saveResult(MonsterRaceResult result) {
        jdbcClient
                .sql(UPSERT_RESULT)
                .param(result.raceId())
                .param(result.first())
                .param(result.second())
                .param(result.oddRate())
                .update();
    }

    /**
     * @return the bet total by lane (1 to 8); a lane nobody bet on since the table was created has no entry
     */
    public Map<Integer, Long> findBets() {
        final Map<Integer, Long> bets = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_BETS).query((RowCallbackHandler)
                rs -> bets.put(rs.getInt("lane"), rs.getLong("bet_total")));
        return bets;
    }

    public void saveBet(int lane, long sum) {
        jdbcClient.sql(UPSERT_BET).param(lane).param(sum).update();
    }

    public void clearBets() {
        jdbcClient.sql(CLEAR_BETS).update();
    }
}
