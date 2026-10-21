/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.gate.model.CriterionResult;
import app.l2nx.load.gate.model.GateCriterion;
import app.l2nx.load.gate.model.GateThresholds;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.InvalidReason;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GateTest {

    private static final String SCENARIO = "load-crowd-pvp";
    private static final String LOOP = "world-0";
    private static final GateThresholds THRESHOLDS = GateThresholds.defaults();

    @Nested
    class Evaluate {

        @Test
        void evaluate_shouldPassEveryCriterion_whenRunsAreWithinThresholds() {
            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), baseline(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.validRunIds()).containsExactly("a", "b", "c");
            assertThat(actual.results())
                    .extracting(CriterionResult::criterion, CriterionResult::subject, CriterionResult::passed)
                    .containsExactly(
                            tuple(GateCriterion.VALID_RUNS, null, true),
                            tuple(GateCriterion.ENGINE_PASS_P99, LOOP, true),
                            tuple(GateCriterion.OVER_BUDGET_SHARE, LOOP, true),
                            tuple(GateCriterion.CLIENT_LATENCY_P99, "MOVE_TO", true),
                            tuple(GateCriterion.TIMEOUTS_AND_ERRORS, Gate.TIMEOUTS, true),
                            tuple(GateCriterion.TIMEOUTS_AND_ERRORS, Gate.REJECTED, true),
                            tuple(GateCriterion.TIMEOUTS_AND_ERRORS, Gate.STOPPED_LOAD_BOTS, true));
        }

        @Test
        void evaluate_shouldTakeMedianOfRunP99_whenOneRunIsAnOutlier() {
            var runs = List.of(
                    run("a", loop(loop -> loop.passP99Millis(10.0))),
                    run("b", loop(loop -> loop.passP99Millis(90.0))),
                    run("c", loop(loop -> loop.passP99Millis(20.0))));

            var actual = result(Gate.evaluate(runs, baseline(), THRESHOLDS), GateCriterion.ENGINE_PASS_P99);

            assertThat(actual.measured()).isEqualTo(20);
            assertThat(actual.threshold()).isEqualTo(50);
            assertThat(actual.passed()).isTrue();
        }

        @Test
        void evaluate_shouldIgnoreInvalidRuns_whenEnoughValidRunsRemain() {
            var invalid = run("bad", loop(loop -> loop.passP99Millis(500.0))).toBuilder()
                    .validity(RunValidity.INVALID)
                    .invalidReasons(List.of(InvalidReason.GENERATOR_LAG))
                    .build();

            var actual = Gate.evaluate(List.of(run("a"), invalid, run("b"), run("c")), baseline(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.validRunIds()).containsExactly("a", "b", "c");
            assertThat(actual.invalidRunIds()).containsExactly("bad");
        }

        @Test
        void evaluate_shouldFailValidRuns_whenFewerValidRunsThanRequired() {
            var invalid = run("bad").toBuilder()
                    .validity(RunValidity.INVALID)
                    .invalidReasons(List.of(InvalidReason.LOAD_BOTS_STOPPED))
                    .build();

            var actual = Gate.evaluate(List.of(run("a"), invalid, run("b")), baseline(), THRESHOLDS);

            assertThat(actual.passed()).isFalse();
            assertThat(result(actual, GateCriterion.VALID_RUNS))
                    .extracting(CriterionResult::measured, CriterionResult::threshold, CriterionResult::passed)
                    .containsExactly(2.0, 3.0, false);
            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() != GateCriterion.VALID_RUNS)
                    .allMatch(CriterionResult::passed);
        }

        @Test
        void evaluate_shouldFailEveryCriterionWithReason_whenNoRunIsValid() {
            var invalid = run("bad").toBuilder().validity(RunValidity.INVALID).build();

            var actual = Gate.evaluate(List.of(invalid), baseline(), THRESHOLDS);

            assertThat(actual.results()).noneMatch(CriterionResult::passed);
            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() != GateCriterion.VALID_RUNS)
                    .extracting(CriterionResult::measured, CriterionResult::reason)
                    .containsOnly(tuple(null, "no valid runs"));
        }

        @Test
        void evaluate_shouldFailEngineCriteria_whenRunHasNoServerMeters() {
            var withoutServer = run("b").toBuilder().server(null).build();

            var actual = Gate.evaluate(List.of(run("a"), withoutServer, run("c")), baseline(), THRESHOLDS);

            assertThat(actual.passed()).isFalse();
            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.ENGINE_PASS_P99
                            || result.criterion() == GateCriterion.OVER_BUDGET_SHARE)
                    .extracting(CriterionResult::measured, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(
                            tuple(null, false, "no engine loop meters in runs [b]"),
                            tuple(null, false, "no engine loop meters in runs [b]"));
        }

        @Test
        void evaluate_shouldFailLoop_whenRunLacksItsMeters() {
            var second = loop(loop -> loop);
            var runs = List.of(
                    run("a", Map.of(LOOP, loop(loop -> loop), "world-1", second)),
                    run("b", Map.of(LOOP, loop(loop -> loop), "world-1", second)),
                    run("c", Map.of(LOOP, loop(loop -> loop))));

            var actual = Gate.evaluate(runs, baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.ENGINE_PASS_P99)
                    .extracting(CriterionResult::subject, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(
                            tuple(LOOP, true, null), tuple("world-1", false, "no meters of loop world-1 in runs [c]"));
        }

        @Test
        void evaluate_shouldFailLoop_whenRunHasNoPasses() {
            var idle = run("c", loop(loop -> loop.passes(0).overBudget(0)));

            var actual = Gate.evaluate(List.of(run("a"), run("b"), idle), baseline(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.OVER_BUDGET_SHARE))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(false, "no passes of loop world-0 in runs [c]");
        }

        @Test
        void evaluate_shouldFailEngineCriteria_whenRunHasNoPassP99() {
            var withoutP99 = run(
                    "c",
                    loop(loop -> loop.passP50Millis(null).passP99Millis(null).passMaxMillis(null)));

            var actual = Gate.evaluate(List.of(run("a"), run("b"), withoutP99), baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.ENGINE_PASS_P99
                            || result.criterion() == GateCriterion.OVER_BUDGET_SHARE)
                    .extracting(CriterionResult::measured, CriterionResult::passed, CriterionResult::reason)
                    .containsOnly(tuple(null, false, "no pass p99 of loop world-0 in runs [c]"));
        }

        @Test
        void evaluate_shouldFailClientLatency_whenRunLacksBaselineIntent() {
            var withoutIntent = run("c").toBuilder().intents(Map.of()).build();

            var actual = Gate.evaluate(List.of(run("a"), run("b"), withoutIntent), baseline(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.CLIENT_LATENCY_P99))
                    .extracting(CriterionResult::measured, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(null, false, "no acknowledged MOVE_TO intents in runs [c]");
        }

        @Test
        void evaluate_shouldFailClientLatency_whenBaselineIsInvalid() {
            var baseline = baseline().toBuilder().validity(RunValidity.INVALID).build();

            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.CLIENT_LATENCY_P99))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(false, "baseline base is invalid");
        }

        @Test
        void evaluate_shouldFailClientLatency_whenRunHasIntentKindAbsentFromBaseline() {
            var withSay = run("a").toBuilder()
                    .intents(Map.of(IntentKind.MOVE_TO, intent(10), IntentKind.SAY, intent(900)))
                    .build();

            var actual = Gate.evaluate(List.of(withSay, run("b"), run("c")), baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.CLIENT_LATENCY_P99)
                    .extracting(CriterionResult::subject, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(
                            tuple("MOVE_TO", true, null),
                            tuple("SAY", false, "no SAY intents in baseline base, present in runs [a]"));
        }

        @Test
        void evaluate_shouldThrow_whenRunIsOfAnotherScenario() {
            var foreign = run("x").toBuilder().scenario("load-roam").build();

            assertThatThrownBy(() -> Gate.evaluate(List.of(run("a"), foreign), baseline(), THRESHOLDS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("[x]");
        }

        @ParameterizedTest(name = "pass p99 {0} ms -> passed {1}")
        @CsvSource({"49.99, true", "50, true", "50.01, false"})
        void evaluate_shouldCompareEnginePassP99WithBudget_whenAtBoundary(double p99, boolean expected) {
            var runs = List.of(
                    run("a", loop(loop -> loop.passP99Millis(p99))),
                    run("b", loop(loop -> loop.passP99Millis(p99))),
                    run("c", loop(loop -> loop.passP99Millis(p99))));

            var actual = result(Gate.evaluate(runs, baseline(), THRESHOLDS), GateCriterion.ENGINE_PASS_P99);

            assertThat(actual.passed()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} over budget of 1000 passes -> passed {1}")
        @CsvSource({"0, true", "1, true", "2, false"})
        void evaluate_shouldCompareOverBudgetShare_whenAtBoundary(long overBudget, boolean expected) {
            var runs = List.of(
                    run("a", loop(loop -> loop.passes(1000).overBudget(overBudget))),
                    run("b", loop(loop -> loop.passes(1000).overBudget(overBudget))),
                    run("c", loop(loop -> loop.passes(1000).overBudget(overBudget))));

            var actual = result(Gate.evaluate(runs, baseline(), THRESHOLDS), GateCriterion.OVER_BUDGET_SHARE);

            assertThat(actual.measured()).isEqualTo(overBudget / 1000.0);
            assertThat(actual.passed()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "client p99 {0} ms against baseline 10 ms -> passed {1}")
        @CsvSource({"9, true", "11, true", "11.01, false"})
        void evaluate_shouldCompareClientP99WithBaseline_whenAtBoundary(double p99, boolean expected) {
            var runs = List.of(run("a", p99), run("b", p99), run("c", p99));

            var actual = result(Gate.evaluate(runs, baseline(), THRESHOLDS), GateCriterion.CLIENT_LATENCY_P99);

            assertThat(actual.threshold()).isCloseTo(11, within(1e-9));
            assertThat(actual.passed()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "timeout share {0}, rejected share {1}, stopped share {2} -> passed {3}, {4}, {5}")
        @CsvSource({
            "0.005, 0, 0, true, true, true",
            "0.0051, 0, 0, false, true, true",
            "0, 0.005, 0, true, true, true",
            "0, 0.0051, 0, true, false, true",
            "0, 0, 0.005, true, true, true",
            "0, 0, 0.0051, true, true, false"
        })
        void evaluate_shouldCompareEachShareWithTheLimit_whenAtBoundary(
                double timeoutShare,
                double rejectedShare,
                double stoppedShare,
                boolean timeoutsPassed,
                boolean rejectedPassed,
                boolean stoppedPassed) {
            var run = run("a").toBuilder()
                    .timeoutShare(timeoutShare)
                    .rejectedShare(rejectedShare)
                    .stoppedShare(stoppedShare)
                    .build();
            var runs = List.of(
                    run,
                    run.toBuilder().runId("b").build(),
                    run.toBuilder().runId("c").build());

            var actual = Gate.evaluate(runs, baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.TIMEOUTS_AND_ERRORS)
                    .extracting(CriterionResult::subject, CriterionResult::passed)
                    .containsExactly(
                            tuple(Gate.TIMEOUTS, timeoutsPassed),
                            tuple(Gate.REJECTED, rejectedPassed),
                            tuple(Gate.STOPPED_LOAD_BOTS, stoppedPassed));
        }
    }

    private static CriterionResult result(GateVerdict verdict, GateCriterion criterion) {
        return verdict.results().stream()
                .filter(result -> result.criterion() == criterion)
                .findFirst()
                .orElseThrow();
    }

    private static RunSummary baseline() {
        return run("base");
    }

    private static RunSummary run(String runId) {
        return run(runId, loop(loop -> loop));
    }

    private static RunSummary run(String runId, double clientP99Millis) {
        return run(runId).toBuilder()
                .intents(Map.of(IntentKind.MOVE_TO, intent(clientP99Millis)))
                .build();
    }

    private static RunSummary run(String runId, LoopStats loop) {
        return run(runId, Map.of(LOOP, loop));
    }

    private static RunSummary run(String runId, Map<String, LoopStats> loops) {
        return RunSummary.builder()
                .runId(runId)
                .scenario(SCENARIO)
                .protocol("in-memory")
                .seed(42)
                .validity(RunValidity.VALID)
                .invalidReasons(List.of())
                .intents(Map.of(IntentKind.MOVE_TO, intent(10)))
                .generatorLagP99Millis(1)
                .timeoutShare(0.001)
                .rejectedShare(0.001)
                .stoppedShare(0)
                .server(ServerStats.builder().loops(loops).build())
                .build();
    }

    private static LoopStats loop(UnaryOperator<LoopStats.LoopStatsBuilder> customizer) {
        return customizer
                .apply(LoopStats.builder()
                        .passes(6000)
                        .passP50Millis(5.0)
                        .passP99Millis(20.0)
                        .overBudget(1)
                        .phaseMeanMillis(Map.of()))
                .build();
    }

    private static IntentStats intent(double p99Millis) {
        return IntentStats.builder()
                .sent(1000)
                .acknowledged(1000)
                .p50Millis(p99Millis / 2)
                .p99Millis(p99Millis)
                .p999Millis(p99Millis)
                .maxMillis(p99Millis)
                .build();
    }
}
