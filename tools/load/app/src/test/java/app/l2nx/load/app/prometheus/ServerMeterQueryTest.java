/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ServerMeterQueryTest {
    private static final Pattern WINDOW = Pattern.compile("(?:\\[|offset )([^])\\s]*)");

    @Nested
    class Expression {
        @Test
        void expression_shouldTakeEveryBucketFromItsStartSampleOrZero() {
            final String actual = ServerMeterQuery.PASS_P99.expression(61_250);

            assertThat(actual)
                    .isEqualTo("histogram_quantile(0.99, sum by (loop, le) (l2nx_engine_pass_seconds_bucket"
                            + " - ((l2nx_engine_pass_seconds_bucket offset 61250ms)"
                            + " or (l2nx_engine_pass_seconds_bucket * 0))))");
        }

        @Test
        void expression_shouldDivideTheJdbcTimeByThePassTime() {
            final String actual = ServerMeterQuery.JDBC_ON_LOOP_SHARE.expression(1_000);

            assertThat(actual)
                    .isEqualTo("sum by (loop) (l2nx_engine_jdbc_on_loop_seconds_sum"
                            + " - ((l2nx_engine_jdbc_on_loop_seconds_sum offset 1000ms)"
                            + " or (l2nx_engine_jdbc_on_loop_seconds_sum * 0)))"
                            + " / sum by (loop) (l2nx_engine_pass_seconds_sum"
                            + " - ((l2nx_engine_pass_seconds_sum offset 1000ms)"
                            + " or (l2nx_engine_pass_seconds_sum * 0)))");
        }

        @Test
        void expression_shouldWriteTheRangeOfAGaugeInMilliseconds() {
            final String actual = ServerMeterQuery.PASS_MAX.expression(61_250);

            assertThat(actual).isEqualTo("max by (loop) (max_over_time(l2nx_engine_pass_seconds_max[61250ms]))");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ServerMeterQuery.class)
        void expression_shouldGiveEveryRangeAndOffsetTheWindow(ServerMeterQuery query) {
            final String actual = query.expression(61_250);

            assertThat(WINDOW.matcher(actual).results().map(window -> window.group(1)))
                    .isNotEmpty()
                    .containsOnly("61250ms");
            assertThat(actual).doesNotContain("%").doesNotContain("increase(");
        }
    }

    @Nested
    class Targets {
        @Test
        void lastScrape_shouldTakeTheOldestNewestScrapeOfTheJobs() {
            final String actual = ServerMeterQuery.lastScrape(List.of("game", "login"));

            assertThat(actual).isEqualTo("min(timestamp(up{job=~\"game|login\"}))");
        }

        @Test
        void now_shouldReadPrometheusTimeAsAVector() {
            final String actual = ServerMeterQuery.now();

            assertThat(actual).isEqualTo("vector(time())");
        }

        @Test
        void upThroughout_shouldReachOneScrapeIntervalBeforeTheWindow() {
            final String actual = ServerMeterQuery.upThroughout("game", 61_250, Duration.ofMillis(1_500));

            assertThat(actual).isEqualTo("min_over_time(up{job=\"game\"}[62750ms])");
        }

        @Test
        void processStart_shouldSelectTheJob() {
            final String actual = ServerMeterQuery.processStart("game");

            assertThat(actual).isEqualTo("process_start_time_seconds{job=\"game\"}");
        }

        @Test
        void up_shouldSelectTheJob() {
            final String actual = ServerMeterQuery.up("game");

            assertThat(actual).isEqualTo("up{job=\"game\"}");
        }
    }
}
