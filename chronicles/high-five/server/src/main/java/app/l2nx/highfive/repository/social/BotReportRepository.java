/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.social;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.social.model.BotReport;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class BotReportRepository {
    private static final String SELECT_ALL =
            "SELECT bot_character_id, reporter_character_id, reported_at FROM bot_reports";
    private static final String DELETE_ALL = "DELETE FROM bot_reports";
    private static final String INSERT =
            "INSERT INTO bot_reports (bot_character_id, reporter_character_id, reported_at) VALUES (?, ?, ?)";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<BotReport> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> BotReport.builder()
                        .botId(registry.handleOf(rs.getObject("bot_character_id", UUID.class)))
                        .reporterId(registry.handleOf(rs.getObject("reporter_character_id", UUID.class)))
                        .reportedAt(JdbcTimes.instant(rs, "reported_at"))
                        .build())
                .list();
    }

    /**
     * Replaces the stored reports; a report whose bot or reporter was deleted since is dropped.
     */
    @Transactional
    public void replaceAll(Collection<BotReport> reports) {
        jdbcClient.sql(DELETE_ALL).update();
        if (reports.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT,
                reports.stream()
                        .flatMap(report -> registry.findId(report.botId()).stream()
                                .flatMap(botId -> registry.findId(report.reporterId()).stream()
                                        .map(reporterId ->
                                                new Object[] {botId, reporterId, JdbcTimes.utc(report.reportedAt())})))
                        .toList());
    }
}
