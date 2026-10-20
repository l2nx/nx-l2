/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.social.BotReportRepository;
import app.l2nx.highfive.repository.social.model.BotReport;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

// The repository reads and rewrites the whole table, so its tests must not interleave.
@ResourceLock("bot_reports")
class BotReportRepositoryTest extends RepositoryTest {
    private final BotReportRepository repository = new BotReportRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnHandlesAndEpochMillis() {
            JDBC.sql("DELETE FROM bot_reports").update();
            final UUID bot = insertCharacter();
            final UUID reporter = insertCharacter();
            insertReport(bot, reporter);

            final List<BotReport> actual = repository.findAll();

            assertThat(actual).containsExactly(new BotReport(registry.handleOf(bot), registry.handleOf(reporter), NOW));
        }
    }

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldReplaceEveryStoredReport() {
            insertReport(insertCharacter(), insertCharacter());
            final UUID bot = insertCharacter();
            final UUID reporter = insertCharacter();
            final List<BotReport> reports =
                    List.of(new BotReport(registry.handleOf(bot), registry.handleOf(reporter), NOW));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(reports));

            assertThat(reports()).containsExactly(tuple(bot, reporter, NOW));
        }

        @Test
        void replaceAll_shouldDropReport_whenCharacterWasDeleted() {
            final UUID bot = insertCharacter();
            final UUID reporter = insertCharacter();
            final int deleted = registry.handleOf(insertCharacter());
            registry.release(deleted);
            final List<BotReport> reports = List.of(
                    new BotReport(registry.handleOf(bot), registry.handleOf(reporter), NOW),
                    new BotReport(deleted, registry.handleOf(reporter), NOW));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(reports));

            assertThat(reports()).containsExactly(tuple(bot, reporter, NOW));
            assertThat(registry.findId(deleted)).isEmpty();
        }

        @Test
        void replaceAll_shouldClearTable_whenNoReports() {
            insertReport(insertCharacter(), insertCharacter());

            repository.replaceAll(List.of());

            assertThat(reports()).isEmpty();
        }
    }

    private static void insertReport(UUID bot, UUID reporter) {
        JDBC.sql("INSERT INTO bot_reports (bot_character_id, reporter_character_id, reported_at) VALUES (?, ?, ?)")
                .param(bot)
                .param(reporter)
                .param(JdbcTimes.utc(NOW))
                .update();
    }

    private static List<Tuple> reports() {
        return JDBC.sql("SELECT bot_character_id, reporter_character_id, reported_at FROM bot_reports")
                .query((rs, rowNum) -> tuple(
                        rs.getObject("bot_character_id", UUID.class),
                        rs.getObject("reporter_character_id", UUID.class),
                        JdbcTimes.instant(rs, "reported_at")))
                .list();
    }
}
