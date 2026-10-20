/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.minigame;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.minigame.model.Lottery;
import app.l2nx.highfive.repository.minigame.model.LotteryResult;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LotteryRepository {
    private static final String SELECT_LAST = """
        SELECT id, prize_pool, next_prize_pool, ends_at, finished
        FROM lotteries
        ORDER BY id DESC
        LIMIT 1\
        """;
    private static final String INSERT_LOTTERY = """
        INSERT INTO lotteries (ends_at, prize_pool, next_prize_pool)
        VALUES (?, ?, ?)
        RETURNING id\
        """;
    private static final String UPDATE_PRIZE = "UPDATE lotteries SET prize_pool = ?, next_prize_pool = ? WHERE id = ?";
    private static final String UPDATE_FINISHED = """
        UPDATE lotteries
        SET finished = true, prize_pool = ?, next_prize_pool = ?, winning_mask1 = ?, winning_mask2 = ?,
            first_prize = ?, second_prize = ?, third_prize = ?
        WHERE id = ?\
        """;
    private static final String SELECT_RESULT = """
        SELECT winning_mask1, winning_mask2, first_prize, second_prize, third_prize
        FROM lotteries
        WHERE id = ? AND finished\
        """;

    private final JdbcClient jdbcClient;

    public Optional<Lottery> findLast() {
        return jdbcClient
                .sql(SELECT_LAST)
                .query((rs, rowNum) -> Lottery.builder()
                        .id(rs.getInt("id"))
                        .prize(rs.getLong("prize_pool"))
                        .newPrize(rs.getLong("next_prize_pool"))
                        .endDate(JdbcTimes.instant(rs, "ends_at"))
                        .finished(rs.getBoolean("finished"))
                        .build())
                .optional();
    }

    public int create(Instant endDate, long prize, long newPrize) {
        return jdbcClient
                .sql(INSERT_LOTTERY)
                .param(JdbcTimes.utc(endDate))
                .param(prize)
                .param(newPrize)
                .query(Integer.class)
                .single();
    }

    public void updatePrize(int id, long prize, long newPrize) {
        jdbcClient.sql(UPDATE_PRIZE).param(prize).param(newPrize).param(id).update();
    }

    public void finish(int id, long prize, long newPrize, LotteryResult result) {
        jdbcClient
                .sql(UPDATE_FINISHED)
                .param(prize)
                .param(newPrize)
                .param(result.number1())
                .param(result.number2())
                .param(result.prize1())
                .param(result.prize2())
                .param(result.prize3())
                .param(id)
                .update();
    }

    /**
     * @return the draw, empty while the lottery is not finished
     */
    public Optional<LotteryResult> findResult(int id) {
        return jdbcClient
                .sql(SELECT_RESULT)
                .param(id)
                .query((rs, rowNum) -> LotteryResult.builder()
                        .number1(rs.getInt("winning_mask1"))
                        .number2(rs.getInt("winning_mask2"))
                        .prize1(rs.getLong("first_prize"))
                        .prize2(rs.getLong("second_prize"))
                        .prize3(rs.getLong("third_prize"))
                        .build())
                .optional();
    }
}
