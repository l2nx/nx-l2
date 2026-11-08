/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.load.report.model.CapacityLimit;
import app.l2nx.load.report.model.LevelStats;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CapacityJudgeTest {
    private static final Duration BUDGET = Duration.ofMillis(50);
    private static final double MAX_SHARE = 0.001;
    private static final double MAX_FAILED_SHARE = 0.005;

    @Nested
    class Judge {
        @Test
        void judge_shouldTakeTheLastLevelWithinBudget_whenALaterOneExceedsIt() {
            var actual = CapacityJudge.judge(
                    List.of(level(500, 2.0), level(1_000, 12.0), level(1_500, 51.0)),
                    BUDGET,
                    MAX_SHARE,
                    MAX_FAILED_SHARE,
                    MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(1_000);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.PASS_BUDGET);
            assertThat(actual.reason()).contains("1500");
        }

        @Test
        void judge_shouldStopAtTheOverBudgetShare_whenThePassP99IsWithinBudget() {
            var crowded = level(1_500, 20.0).toBuilder().overBudgetShare(0.002).build();

            var actual = CapacityJudge.judge(
                    List.of(level(1_000, 10.0), crowded), BUDGET, MAX_SHARE, MAX_FAILED_SHARE, MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(1_000);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.PASS_BUDGET);
        }

        @Test
        void judge_shouldStopBelowAnInvalidLevel_markedAsLimitedByTheGenerator() {
            var lagging = level(1_500, 10.0).toBuilder().valid(false).build();

            var actual = CapacityJudge.judge(
                    List.of(level(1_000, 10.0), lagging, level(2_000, 10.0)),
                    BUDGET,
                    MAX_SHARE,
                    MAX_FAILED_SHARE,
                    MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(1_000);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.GENERATOR);
        }

        @Test
        void judge_shouldStopAtALevelWithoutGeneratorLagSamples_asInvalid() {
            var unmeasured = level(1_500, 10.0).toBuilder()
                    .generatorLagP99Millis(null)
                    .valid(false)
                    .build();

            var actual = CapacityJudge.judge(
                    List.of(level(1_000, 10.0), unmeasured), BUDGET, MAX_SHARE, MAX_FAILED_SHARE, MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(1_000);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.GENERATOR);
            assertThat(actual.reason()).isEqualTo("level 1500 is invalid: no generator lag sample");
        }

        static Stream<Arguments> failedShares() {
            return Stream.of(
                    Arguments.of("timeouts", (UnaryOperator<LevelStats.LevelStatsBuilder>) b -> b.timeoutShare(0.006)),
                    Arguments.of("rejected", (UnaryOperator<LevelStats.LevelStatsBuilder>) b -> b.rejectedShare(0.006)),
                    Arguments.of("stopped load bots", (UnaryOperator<LevelStats.LevelStatsBuilder>)
                            b -> b.stoppedShare(0.006)),
                    Arguments.of("not at the final state", (UnaryOperator<LevelStats.LevelStatsBuilder>)
                            b -> b.notAtFinalStateShare(0.006)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("failedShares")
        void judge_shouldStopAtALevelThatLostLoadBotsOrIntents_whenItsPassP99IsWithinBudget(
                String share, UnaryOperator<LevelStats.LevelStatsBuilder> change) {
            var failing = change.apply(level(1_500, 10.0).toBuilder()).build();

            var actual = CapacityJudge.judge(
                    List.of(level(1_000, 10.0), failing, level(2_000, 10.0)),
                    BUDGET,
                    MAX_SHARE,
                    MAX_FAILED_SHARE,
                    MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(1_000);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.LOAD_BOTS_FAILED);
            assertThat(actual.reason()).isEqualTo("level 1500: " + share + " 0.600% over 0.500%");
        }

        @Test
        void judge_shouldPassALevel_whenItsSharesAreAtTheLimits() {
            var atLimits = level(1_000, 10.0).toBuilder()
                    .timeoutShare(MAX_FAILED_SHARE)
                    .rejectedShare(MAX_FAILED_SHARE)
                    .stoppedShare(MAX_FAILED_SHARE)
                    .notAtFinalStateShare(MAX_FAILED_SHARE)
                    .build();

            var actual = CapacityJudge.judge(List.of(atLimits), BUDGET, MAX_SHARE, MAX_FAILED_SHARE, MAX_FAILED_SHARE);

            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.LAST_LEVEL);
        }

        @Test
        void judge_shouldGiveNoBotsWithTheReason_whenTheFirstLevelFails() {
            var actual = CapacityJudge.judge(
                    List.of(level(500, 60.0)), BUDGET, MAX_SHARE, MAX_FAILED_SHARE, MAX_FAILED_SHARE);

            assertThat(actual.bots()).isNull();
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.PASS_BUDGET);
            assertThat(actual.reason()).startsWith("no level passed");
        }

        @Test
        void judge_shouldStopAtALevelWithoutServerMeters() {
            var blind = level(1_000, 10.0).toBuilder().passP99Millis(null).build();

            var actual = CapacityJudge.judge(
                    List.of(level(500, 2.0), blind), BUDGET, MAX_SHARE, MAX_FAILED_SHARE, MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(500);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.SERVER_METERS);
        }

        @Test
        void judge_shouldMarkTheLastLevel_whenEveryLevelPasses() {
            var actual = CapacityJudge.judge(
                    List.of(level(500, 2.0), level(1_000, 50.0)),
                    BUDGET,
                    MAX_SHARE,
                    MAX_FAILED_SHARE,
                    MAX_FAILED_SHARE);

            assertThat(actual.bots()).isEqualTo(1_000);
            assertThat(actual.limitedBy()).isEqualTo(CapacityLimit.LAST_LEVEL);
        }

        @Test
        void judge_shouldRefuse_whenThereAreNoLevels() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            CapacityJudge.judge(List.of(), BUDGET, MAX_SHARE, MAX_FAILED_SHARE, MAX_FAILED_SHARE));
        }
    }

    private static LevelStats level(int bots, double passP99Millis) {
        return LevelStats.builder()
                .bots(bots)
                .passP99Millis(passP99Millis)
                .overBudgetShare(0.0)
                .inputP99Millis(Map.of())
                .generatorLagP99Millis(1.0)
                .valid(true)
                .build();
    }
}
