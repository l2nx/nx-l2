/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.olympiad.model.OlympiadStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OlympiadStatusRepository {
    // Index = the Olympiad period constant.
    private static final List<String> PERIODS = List.of("COMPETITION", "VALIDATION");

    private static final String SELECT = """
        SELECT current_cycle, period, olympiad_ends_at, validation_ends_at, next_weekly_change_at
        FROM olympiad_status
        WHERE id = 0\
        """;
    private static final String UPSERT = """
        INSERT INTO olympiad_status (
            id, current_cycle, period, olympiad_ends_at, validation_ends_at, next_weekly_change_at)
        VALUES (0, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET current_cycle = EXCLUDED.current_cycle, period = EXCLUDED.period,
            olympiad_ends_at = EXCLUDED.olympiad_ends_at, validation_ends_at = EXCLUDED.validation_ends_at,
            next_weekly_change_at = EXCLUDED.next_weekly_change_at\
        """;

    private final JdbcClient jdbcClient;

    public Optional<OlympiadStatus> find() {
        return jdbcClient
                .sql(SELECT)
                .query((rs, rowNum) -> OlympiadStatus.builder()
                        .currentCycle(rs.getInt("current_cycle"))
                        .period(PERIODS.indexOf(rs.getString("period")))
                        .olympiadEnd(JdbcTimes.instant(rs, "olympiad_ends_at"))
                        .validationEnd(JdbcTimes.instant(rs, "validation_ends_at"))
                        .nextWeeklyChange(JdbcTimes.instant(rs, "next_weekly_change_at"))
                        .build())
                .optional();
    }

    public void save(OlympiadStatus status) {
        jdbcClient
                .sql(UPSERT)
                .param(status.currentCycle())
                .param(PERIODS.get(status.period()))
                .param(JdbcTimes.utc(status.olympiadEnd()))
                .param(JdbcTimes.utc(status.validationEnd()))
                .param(JdbcTimes.utc(status.nextWeeklyChange()))
                .update();
    }
}
