/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.gate.model.CriterionResult;
import app.l2nx.load.gate.model.GateCriterion;
import app.l2nx.load.gate.model.GateThresholds;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.report.model.InputStats;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.InvalidReason;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.RunEnvironment;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import app.l2nx.load.report.model.StepStats;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class GateTest {

    private static final String SCENARIO = "load-crowd-pvp";
    private static final String LOOP = "world-0";
    private static final String DRAIN = "l2nx-game-packets";
    private static final GateThresholds THRESHOLDS =
            GateThresholds.defaults().toBuilder().runs(3).build();
    private static final String TYPE = "app.l2nx.highfive.movement.command.MoveTo";
    private static final String OTHER_TYPE = "app.l2nx.highfive.network.clientpackets.Say2";
    private static final Instant STARTED_AT = Instant.parse("2026-11-04T08:00:00Z");
    private static final InjectionProfile INJECTION = InjectionProfile.builder()
            .bots(500)
            .ramp(Duration.ofSeconds(30))
            .warmUp(Duration.ofSeconds(30))
            .steady(Duration.ofMinutes(5))
            .rampDown(Duration.ofSeconds(10))
            .build();
    private static final RunEnvironment ENVIRONMENT = RunEnvironment.builder()
            .operatingSystem("Linux amd64")
            .kernel("6.8.0")
            .cpuModel("AMD Ryzen 5 5600X")
            .logicalCores(6)
            .cpuset("0-5")
            .jvm("OpenJDK 25")
            .build();

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
                            tuple(GateCriterion.INPUT_TIME_P99, TYPE, true),
                            tuple(GateCriterion.TIMEOUTS_AND_ERRORS, Gate.TIMEOUTS, true),
                            tuple(GateCriterion.TIMEOUTS_AND_ERRORS, Gate.REJECTED, true),
                            tuple(GateCriterion.TIMEOUTS_AND_ERRORS, Gate.STOPPED_LOAD_BOTS, true),
                            tuple(GateCriterion.NOT_AT_FINAL_STATE, null, true),
                            tuple(GateCriterion.OWNERSHIP_VIOLATIONS, LOOP, true));
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
                    .invalidReasons(List.of(InvalidReason.GENERATOR_LAG))
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
        void evaluate_shouldFailInputTime_whenTheBaselineHasFewerValidRunsThanRequired() {
            var baseline = List.of(
                    run("base1").toBuilder().validity(RunValidity.INVALID).build(), run("base2"), run("base3"));

            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.INPUT_TIME_P99))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(false, "the baseline has 2 valid runs [base2, base3] of 3 needed");
            assertThat(actual.baselineRunIds()).containsExactly("base2", "base3");
            assertThat(actual.invalidRunIds()).containsExactly("base1");
        }

        @Test
        void evaluate_shouldReportButNotGateAType_whenItIsAbsentFromTheBaseline() {
            var withOther = withInputs(run("a"), Map.of(TYPE, input(1.0), OTHER_TYPE, input(0.2)));

            var actual = Gate.evaluate(List.of(withOther, run("b"), run("c")), baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.INPUT_TIME_P99)
                    .extracting(
                            CriterionResult::subject,
                            CriterionResult::measured,
                            CriterionResult::passed,
                            CriterionResult::reason)
                    .containsExactly(tuple(TYPE, 1.0, true, null), tuple(OTHER_TYPE, 0.2, true, Gate.NOT_GATED));
        }

        @ParameterizedTest(name = "{0} inputs in the baseline median, {1} in the run median -> gated {2}")
        @CsvSource({"99, 99, false", "100, 99, true", "99, 100, true"})
        void evaluate_shouldGateATypeFrom100InputsInTheBaselineOrTheRunMedian(
                long baselineCount, long runCount, boolean expected) {
            var baseline = Stream.of("base1", "base2", "base3")
                    .map(id -> withInputs(run(id), Map.of(TYPE, input(1.0, baselineCount))))
                    .toList();
            var slow = Stream.of("a", "b", "c")
                    .map(id -> withInputs(run(id), Map.of(TYPE, input(50.0, runCount))))
                    .toList();

            var actual = result(Gate.evaluate(slow, baseline, THRESHOLDS), GateCriterion.INPUT_TIME_P99);

            assertThat(actual.passed()).isEqualTo(!expected);
            assertThat(actual.measured()).isEqualTo(50.0);
            assertThat(actual.reason()).isEqualTo(expected ? null : Gate.NOT_GATED);
        }

        @Test
        void evaluate_shouldFailANewInputClass_whenMostRunsHaveEnoughOfIt() {
            var runs = List.of(
                    withInputs(run("a"), Map.of(TYPE, input(1.0), OTHER_TYPE, input(0.2, 500))),
                    withInputs(run("b"), Map.of(TYPE, input(1.0), OTHER_TYPE, input(0.2, 500))),
                    run("c"));

            var actual = Gate.evaluate(runs, baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> OTHER_TYPE.equals(result.subject()))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .as("the run median is 500, so the class is gated and the baseline lacks it")
                    .containsExactly(tuple(false, Gate.NEW_INPUT_CLASS));
        }

        @Test
        void evaluate_shouldFailANewInputClass_whenTheRunsHaveEnoughOfIt() {
            var runs = Stream.of("a", "b", "c")
                    .map(id -> withInputs(run(id), Map.of(TYPE, input(1.0), OTHER_TYPE, input(0.2))))
                    .toList();

            var actual = Gate.evaluate(runs, baseline(), THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.INPUT_TIME_P99)
                    .extracting(
                            CriterionResult::subject,
                            CriterionResult::threshold,
                            CriterionResult::passed,
                            CriterionResult::reason)
                    .containsExactly(
                            tuple(TYPE, 1.5, true, null),
                            tuple(
                                    OTHER_TYPE,
                                    null,
                                    false,
                                    "absent from the baseline: new input class, retake the baseline"));
        }

        @Test
        void evaluate_shouldGateAType_whenMostBaselineRunsHaveIt() {
            var baseline = List.of(run("base1"), run("base2"), withInputs(run("base3"), Map.of()));

            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.INPUT_TIME_P99))
                    .extracting(CriterionResult::subject, CriterionResult::threshold, CriterionResult::passed)
                    .containsExactly(TYPE, 1.5, true);
        }

        @Test
        void evaluate_shouldFailAGatedType_whenARunLacksIt() {
            var actual =
                    Gate.evaluate(List.of(run("a"), run("b"), withInputs(run("c"), Map.of())), baseline(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.INPUT_TIME_P99))
                    .extracting(CriterionResult::subject, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(TYPE, false, "absent from runs [c]");
        }

        @Test
        void evaluate_shouldFailInputTime_whenARunHasNoServerMeters() {
            var withoutServer = run("c").toBuilder().server(null).build();

            var actual = Gate.evaluate(List.of(run("a"), run("b"), withoutServer), baseline(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.INPUT_TIME_P99))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(false, "no input time meters in runs [c]");
        }

        @Test
        void evaluate_shouldFailInputTime_whenNeitherTheBaselineNorTheRunsHaveAnInputSeries() {
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> withInputs(run, Map.of()))
                    .toList();
            var baseline =
                    baseline().stream().map(run -> withInputs(run, Map.of())).toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.INPUT_TIME_P99))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(false, Gate.NO_INPUT_SERIES);
        }

        @Test
        void evaluate_shouldNotGateClientLatency_whenTheGameTimingSetsIt() {
            var slowClient = IntentStats.builder()
                    .sent(1_000)
                    .acknowledged(1_000)
                    .p99Millis(5_000)
                    .build();
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> run.toBuilder()
                            .intents(Map.of(IntentKind.USE_SKILL, slowClient))
                            .build())
                    .toList();

            var actual = Gate.evaluate(runs, baseline(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
        }

        @Test
        void evaluate_shouldPassTheLifecycleSteps_whenTheirP99StaysWithinTheBaseline() {
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> withStep(run, LoadBotState.LOGIN_AUTH, 120))
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> withStep(run, LoadBotState.LOGIN_AUTH, 110))
                    .toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.LIFECYCLE_STEP_P99)
                    .extracting(CriterionResult::subject, CriterionResult::measured, CriterionResult::passed)
                    .containsExactly(tuple("LOGIN_AUTH", 120.0, true));
            assertThat(result(actual, GateCriterion.LIFECYCLE_STEP_P99).threshold())
                    .as("110 ms * 1.10 lies above 110 ms + 10 ms")
                    .isCloseTo(121, within(1e-9));
        }

        @ParameterizedTest(name = "step p99 {0} ms against baseline {1} ms -> limit {2} ms, passed {3}")
        @CsvSource({
            "220, 200, 220, true",
            "220.01, 200, 220, false",
            "15, 5, 15, true",
            "15.01, 5, 15, false",
            "110, 100, 110, true"
        })
        void evaluate_shouldGateTheStepP99OnTheLargerOfTheRatioAndTheAdditiveFloor(
                double p99, double baselineP99, double limit, boolean expected) {
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> withStep(run, LoadBotState.ENTERING, p99))
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> withStep(run, LoadBotState.ENTERING, baselineP99))
                    .toList();

            var actual = result(Gate.evaluate(runs, baseline, THRESHOLDS), GateCriterion.LIFECYCLE_STEP_P99);

            assertThat(actual.threshold()).isCloseTo(limit, within(1e-9));
            assertThat(actual.passed()).isEqualTo(expected);
        }

        @Test
        void evaluate_shouldSkipAStep_whenNeitherTheBaselineNorTheRunsCountedIt() {
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> withStep(withStep(run, LoadBotState.LOGIN_AUTH, 50), LoadBotState.GAME_CONNECT, 0, 0))
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> withStep(run, LoadBotState.LOGIN_AUTH, 50))
                    .toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.LIFECYCLE_STEP_P99)
                    .extracting(CriterionResult::subject, CriterionResult::passed)
                    .containsExactly(tuple("LOGIN_AUTH", true));
        }

        @Test
        void evaluate_shouldFailAStep_whenARunDidNotCountIt() {
            var runs = List.of(
                    withStep(run("a"), LoadBotState.LOGIN_AUTH, 50),
                    withStep(run("b"), LoadBotState.LOGIN_AUTH, 50),
                    withStep(run("c"), LoadBotState.LOGIN_AUTH, 0, 0));
            var baseline = baseline().stream()
                    .map(run -> withStep(run, LoadBotState.LOGIN_AUTH, 50))
                    .toList();

            var actual = result(Gate.evaluate(runs, baseline, THRESHOLDS), GateCriterion.LIFECYCLE_STEP_P99);

            assertThat(actual)
                    .extracting(CriterionResult::subject, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly("LOGIN_AUTH", false, "no LOGIN_AUTH steps in runs [c]");
        }

        @Test
        void evaluate_shouldReportTheLifecycleStepsAsNotApplicableAndPass_whenThereIsNoBaseline() {
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> withStep(run, LoadBotState.LOGIN_AUTH, 5_000))
                    .toList();

            var actual = Gate.evaluate(runs, List.of(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.LIFECYCLE_STEP_P99)
                    .extracting(CriterionResult::measured, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(tuple(null, true, Gate.NO_BASELINE));
        }

        @Test
        void evaluate_shouldRefuseTheBaseline_whenItWasTakenWithAnotherReportVersion() {
            var old = baseline().stream()
                    .map(run -> run.toBuilder().reportVersion(1).build())
                    .toList();

            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), old, THRESHOLDS);

            assertThat(actual.passed()).isFalse();
            assertThat(result(actual, GateCriterion.COMPARABLE_RUNS).reason())
                    .isEqualTo(
                            "runs [base1, base2, base3] were taken with report version [1], the gate reads version 2:"
                                    + " take them again");
        }

        static Stream<Arguments> incomparableRuns() {
            return Stream.of(
                    Arguments.of(
                            "another scenario",
                            run("x").toBuilder().scenario("load-roam").build(),
                            "runs [x] differ from base1 in the scenario (load-crowd-pvp)"),
                    Arguments.of(
                            "another final state",
                            run("x").toBuilder()
                                    .finalState(LoadBotState.SERVER_LIST)
                                    .build(),
                            "runs [x] differ from base1 in the final state (IN_GAME)"),
                    Arguments.of(
                            "another injection",
                            run("x").toBuilder()
                                    .injection(INJECTION.toBuilder().bots(100).build())
                                    .build(),
                            "runs [x] differ from base1 in the injection (" + INJECTION + ")"),
                    Arguments.of(
                            "another protocol",
                            run("x").toBuilder().protocol("p267").build(),
                            "runs [x] differ from base1 in the protocol (in-memory)"),
                    Arguments.of(
                            "another intent timeout",
                            run("x").toBuilder()
                                    .intentTimeout(Duration.ofSeconds(1))
                                    .build(),
                            "runs [x] differ from base1 in the intent timeout (PT5S)"),
                    Arguments.of(
                            "another lifecycle timeout",
                            run("x").toBuilder()
                                    .lifecycleTimeout(Duration.ofSeconds(1))
                                    .build(),
                            "runs [x] differ from base1 in the lifecycle timeout (PT30S)"),
                    Arguments.of(
                            "another measured phase",
                            run("x").toBuilder()
                                    .measuredPhase(MeasuredPhase.RAMP)
                                    .build(),
                            "runs [x] differ from base1 in the measured phase (STEADY)"),
                    Arguments.of(
                            "another machine",
                            run("x").toBuilder()
                                    .environment(ENVIRONMENT.toBuilder()
                                            .logicalCores(2)
                                            .build())
                                    .build(),
                            "runs [x] differ from base1 in the environment (" + ENVIRONMENT + ")"),
                    Arguments.of(
                            "another CPU affinity",
                            run("x").toBuilder().cpuAffinity("server 0-1").build(),
                            "runs [x] differ from base1 in the CPU affinity (server 0-3)"),
                    Arguments.of(
                            "a capacity run",
                            run("x").toBuilder()
                                    .injection(INJECTION.toBuilder()
                                            .levels(InjectionProfile.Levels.builder()
                                                    .increment(250)
                                                    .count(2)
                                                    .duration(Duration.ofSeconds(90))
                                                    .ramp(Duration.ZERO)
                                                    .build())
                                            .build())
                                    .build(),
                            "runs [x] differ from base1 in the injection (" + INJECTION
                                    + "); runs [x] add load bots in levels: a capacity run changes N inside the run"
                                    + " and is no gate sample"),
                    Arguments.of(
                            "another report version",
                            run("x").toBuilder().reportVersion(1).build(),
                            "runs [x] were taken with report version [1], the gate reads version 2: take them again"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("incomparableRuns")
        void evaluate_shouldFailWithTheReasonAndCompareNothing_whenARunIsNotComparable(
                String name, RunSummary incomparable, String reason) {
            var actual = Gate.evaluate(List.of(run("a"), incomparable, run("b"), run("c")), baseline(), THRESHOLDS);

            assertThat(actual.passed()).isFalse();
            assertThat(actual.results())
                    .extracting(
                            CriterionResult::criterion,
                            CriterionResult::measured,
                            CriterionResult::passed,
                            CriterionResult::reason)
                    .containsExactly(tuple(GateCriterion.COMPARABLE_RUNS, null, false, reason));
        }

        @Test
        void evaluate_shouldReportInputTimeAsNotApplicable_whenLoadBotsStopBeforeTheGameAndNoInputRan() {
            var server = ServerStats.builder().loops(Map.of()).inputs(Map.of()).build();
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> run.toBuilder()
                            .finalState(LoadBotState.SERVER_LIST)
                            .server(server)
                            .build())
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> run.toBuilder()
                            .finalState(LoadBotState.SERVER_LIST)
                            .server(server)
                            .build())
                    .toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            var inputTime = result(actual, GateCriterion.INPUT_TIME_P99);
            assertThat(inputTime.passed()).isTrue();
            assertThat(inputTime.reason()).contains("not applicable").contains("SERVER_LIST");
        }

        @ParameterizedTest(name = "final state {0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"LOGIN_AUTH", "SERVER_LIST", "GAME_CONNECT", "CHARACTER_SELECT", "ENTERING"})
        void evaluate_shouldPassTheEngineCriteriaAsNotApplicable_whenTheLoadBotsStopBeforeTheGame(
                LoadBotState finalState) {
            var server = ServerStats.builder()
                    .loops(Map.of())
                    .inputs(Map.of(TYPE, input(1.0)))
                    .build();
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> run.toBuilder()
                            .finalState(finalState)
                            .server(server)
                            .build())
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> run.toBuilder()
                            .finalState(finalState)
                            .server(server)
                            .build())
                    .toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.results())
                    .filteredOn(result -> result.criterion() == GateCriterion.ENGINE_PASS_P99
                            || result.criterion() == GateCriterion.OVER_BUDGET_SHARE)
                    .extracting(CriterionResult::criterion, CriterionResult::measured, CriterionResult::passed)
                    .containsExactly(
                            tuple(GateCriterion.ENGINE_PASS_P99, null, true),
                            tuple(GateCriterion.OVER_BUDGET_SHARE, null, true));
            assertThat(result(actual, GateCriterion.ENGINE_PASS_P99).reason())
                    .contains("not applicable")
                    .contains(finalState.name());
        }

        @Test
        void evaluate_shouldPassTheEngineCriteriaAsNotApplicable_whenThereAreNoValidRunsBeforeTheGame() {
            var runs = Stream.of(run("a"), run("b"), run("c"))
                    .map(run -> run.toBuilder()
                            .finalState(LoadBotState.GAME_CONNECT)
                            .validity(RunValidity.INVALID)
                            .build())
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> run.toBuilder()
                            .finalState(LoadBotState.GAME_CONNECT)
                            .build())
                    .toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.ENGINE_PASS_P99).passed()).isTrue();
            assertThat(result(actual, GateCriterion.INPUT_TIME_P99).passed()).isFalse();
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

        @ParameterizedTest(name = "input p99 {0} ms against baseline 10 ms -> passed {1}")
        @CsvSource({"9, true", "11, true", "11.01, false"})
        void evaluate_shouldCompareInputP99WithBaseline_whenAtBoundary(double p99, boolean expected) {
            var runs = List.of(run("a", p99), run("b", p99), run("c", p99));
            var baseline = List.of(run("base1", 10), run("base2", 10), run("base3", 10));
            var thresholds = THRESHOLDS.toBuilder().inputP99Floor(Duration.ZERO).build();

            var actual = result(Gate.evaluate(runs, baseline, thresholds), GateCriterion.INPUT_TIME_P99);

            assertThat(actual.threshold()).isCloseTo(11, within(1e-9));
            assertThat(actual.passed()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "baseline {0} ms -> limit {1} ms")
        @CsvSource({"1, 1.5", "5, 5.5", "10, 11"})
        void evaluate_shouldTakeTheLargerOfTheRatioAndTheAdditiveFloor(double baselineP99, double expected) {
            var baseline = List.of(run("base1", baselineP99), run("base2", baselineP99), run("base3", baselineP99));

            var actual = result(
                    Gate.evaluate(List.of(run("a"), run("b"), run("c")), baseline, THRESHOLDS),
                    GateCriterion.INPUT_TIME_P99);

            assertThat(actual.threshold()).isCloseTo(expected, within(1e-9));
        }

        @Test
        void evaluate_shouldTakeTheMedianOfTheValidBaselineRuns_whenOneIsAnOutlierAndOneInvalid() {
            var invalid = run("base0", 1_000).toBuilder()
                    .validity(RunValidity.INVALID)
                    .invalidReasons(List.of(InvalidReason.GENERATOR_LAG))
                    .build();
            var baseline = List.of(invalid, run("base1", 0.9), run("base2", 1.0), run("base3", 5.0));

            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.INPUT_TIME_P99).threshold()).isCloseTo(1.5, within(1e-9));
            assertThat(actual.baselineRunIds()).containsExactly("base1", "base2", "base3");
        }

        @Test
        void evaluate_shouldReportInputTimeAsNotApplicableAndPass_whenThereIsNoBaseline() {
            var actual = Gate.evaluate(List.of(run("a"), run("b"), run("c")), List.of(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.scenario()).isEqualTo(SCENARIO);
            assertThat(actual.baselineRunIds()).isEmpty();
            assertThat(result(actual, GateCriterion.INPUT_TIME_P99))
                    .extracting(CriterionResult::measured, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(null, true, Gate.NO_BASELINE);
        }

        @Test
        void evaluate_shouldStillFailTheAbsoluteCriteria_whenThereIsNoBaseline() {
            var slow = loop(loop -> loop.passP99Millis(90.0));

            var actual = Gate.evaluate(List.of(run("a", slow), run("b", slow), run("c", slow)), List.of(), THRESHOLDS);

            assertThat(actual.passed()).isFalse();
        }

        @Test
        void evaluate_shouldCompareTheRunsWithEachOther_whenThereIsNoBaseline() {
            var other = run("b").toBuilder().protocol("p267").build();

            var actual = Gate.evaluate(List.of(run("a"), other), List.of(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.COMPARABLE_RUNS).reason())
                    .isEqualTo("runs [b] differ from a in the protocol (in-memory)");
        }

        @ParameterizedTest(name = "not at the final state {0} -> passed {1}")
        @CsvSource({"0, true", "0.005, true", "0.0051, false"})
        void evaluate_shouldCompareTheLoadBotsNotAtTheFinalState_whenAtBoundary(double share, boolean expected) {
            var runs = Stream.of("a", "b", "c")
                    .map(id -> run(id).toBuilder().notAtFinalStateShare(share).build())
                    .toList();

            var actual = result(Gate.evaluate(runs, baseline(), THRESHOLDS), GateCriterion.NOT_AT_FINAL_STATE);

            assertThat(actual.measured()).isEqualTo(share);
            assertThat(actual.passed()).isEqualTo(expected);
        }

        @Test
        void evaluate_shouldFailOwnershipViolations_whenOneRunHasOne() {
            var violating = loop(loop -> loop.ownershipViolations(1));

            var actual = Gate.evaluate(List.of(run("a", violating), run("b"), run("c")), baseline(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.OWNERSHIP_VIOLATIONS))
                    .extracting(
                            CriterionResult::subject,
                            CriterionResult::measured,
                            CriterionResult::threshold,
                            CriterionResult::passed)
                    .containsExactly(LOOP, 1.0, 0.0, false);
        }

        @Test
        void evaluate_shouldFailOwnershipViolations_whenARunHasNoEngineMeters() {
            var withoutServer = run("b").toBuilder().server(null).build();

            var actual = Gate.evaluate(List.of(run("a"), withoutServer, run("c")), baseline(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.OWNERSHIP_VIOLATIONS))
                    .extracting(CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(false, "no engine loop meters in runs [b]");
        }

        @Test
        void evaluate_shouldStillCountOwnershipViolations_whenTheLoadBotsStopBeforeTheGame() {
            var violating = loop(loop -> loop.ownershipViolations(1));
            var runs = Stream.of(run("a", violating), run("b", violating), run("c", violating))
                    .map(run -> run.toBuilder()
                            .finalState(LoadBotState.GAME_CONNECT)
                            .build())
                    .toList();
            var baseline = baseline().stream()
                    .map(run -> run.toBuilder()
                            .finalState(LoadBotState.GAME_CONNECT)
                            .build())
                    .toList();

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.OWNERSHIP_VIOLATIONS).passed())
                    .isFalse();
        }

        @Test
        void evaluate_shouldJudgeNoPassesOfTheSessionDrain_whenItHasNoPassTimer() {
            var drain = LoopStats.builder().untimed(true).failures(3).build();
            var runs = Stream.of("a", "b", "c")
                    .map(id -> run(id, Map.of(LOOP, loop(loop -> loop), DRAIN, drain)))
                    .toList();

            var actual = Gate.evaluate(runs, baseline(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
            assertThat(actual.results()).extracting(CriterionResult::subject).doesNotContain(DRAIN);
        }

        @Test
        void evaluate_shouldPass_whenEveryRunHasFreshServers() {
            var runs = List.of(served("a", 0, -1), served("b", 10, 9), served("c", 20, 19));

            var actual = Gate.evaluate(runs, List.of(), THRESHOLDS);

            assertThat(actual.passed()).isTrue();
        }

        @Test
        void evaluate_shouldFailWithTheRun_whenItsServerStartedBeforeThePreviousRun() {
            var runs = List.of(served("a", 0, -1), served("b", 10, 9), served("c", 20, 9));

            var actual = Gate.evaluate(runs, List.of(), THRESHOLDS);

            assertThat(actual.results())
                    .extracting(CriterionResult::criterion, CriterionResult::passed, CriterionResult::reason)
                    .containsExactly(tuple(
                            GateCriterion.COMPARABLE_RUNS,
                            false,
                            "run c used the [game] servers started before the previous run b"));
        }

        @Test
        void evaluate_shouldTakeTheBaselineForThePreviousRun_whenTheRunsFollowIt() {
            var baseline = List.of(served("base1", 0, -1), served("base2", 10, 9), served("base3", 20, 19));
            var runs = List.of(served("a", 30, 19), served("b", 40, 39), served("c", 50, 49));

            var actual = Gate.evaluate(runs, baseline, THRESHOLDS);

            assertThat(result(actual, GateCriterion.COMPARABLE_RUNS).reason())
                    .isEqualTo("run a used the [game] servers started before the previous run base3");
        }

        @Test
        void evaluate_shouldFail_whenARunOnAServerRecordedNoStartTime() {
            var unknown = served("b", 10, 9).toBuilder().serverStartedAt(null).build();

            var actual = Gate.evaluate(List.of(served("a", 0, -1), unknown), List.of(), THRESHOLDS);

            assertThat(result(actual, GateCriterion.COMPARABLE_RUNS).reason())
                    .isEqualTo("runs [b] recorded no server start time");
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

    private static List<RunSummary> baseline() {
        return List.of(run("base1"), run("base2"), run("base3"));
    }

    private static RunSummary served(String runId, int minutes, int serverMinutes) {
        var startedAt = STARTED_AT.plus(Duration.ofMinutes(minutes));
        return run(runId).toBuilder()
                .startedAt(startedAt)
                .serverStartedAt(Map.of("game", STARTED_AT.plus(Duration.ofMinutes(serverMinutes))))
                .serverWindow(ServerWindow.of(startedAt, startedAt.plusSeconds(60)))
                .build();
    }

    private static RunSummary run(String runId) {
        return run(runId, loop(loop -> loop));
    }

    private static RunSummary run(String runId, double inputP99Millis) {
        return withInputs(run(runId), Map.of(TYPE, input(inputP99Millis)));
    }

    private static RunSummary withInputs(RunSummary run, Map<String, InputStats> inputs) {
        return run.toBuilder()
                .server(run.server().toBuilder().inputs(inputs).build())
                .build();
    }

    private static RunSummary withStep(RunSummary run, LoadBotState state, double p99Millis) {
        return withStep(run, state, 1_000, p99Millis);
    }

    private static RunSummary withStep(RunSummary run, LoadBotState state, long count, double p99Millis) {
        var steps = new EnumMap<LoadBotState, StepStats>(LoadBotState.class);
        steps.putAll(run.steps());
        steps.put(
                state,
                StepStats.builder()
                        .count(count)
                        .p50Millis(p99Millis / 2)
                        .p99Millis(p99Millis)
                        .p999Millis(p99Millis)
                        .maxMillis(p99Millis)
                        .build());
        return run.toBuilder().steps(steps).build();
    }

    private static RunSummary run(String runId, LoopStats loop) {
        return run(runId, Map.of(LOOP, loop));
    }

    private static RunSummary run(String runId, Map<String, LoopStats> loops) {
        return RunSummary.builder()
                .reportVersion(RunSummary.REPORT_VERSION)
                .runId(runId)
                .scenario(SCENARIO)
                .measuredPhase(MeasuredPhase.STEADY)
                .finalState(LoadBotState.IN_GAME)
                .injection(INJECTION)
                .intentTimeout(Duration.ofSeconds(5))
                .lifecycleTimeout(Duration.ofSeconds(30))
                .environment(ENVIRONMENT)
                .cpuAffinity("server 0-3")
                .protocol("in-memory")
                .seed(42)
                .validity(RunValidity.VALID)
                .invalidReasons(List.of())
                .intents(Map.of(IntentKind.MOVE_TO, intent(10)))
                .steps(Map.of())
                .generatorLagP99Millis(1)
                .timeoutShare(0.001)
                .rejectedShare(0.001)
                .stoppedShare(0)
                .server(ServerStats.builder()
                        .loops(loops)
                        .inputs(Map.of(TYPE, input(1.0)))
                        .build())
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

    private static InputStats input(double p99Millis) {
        return input(p99Millis, 10_000);
    }

    private static InputStats input(double p99Millis, long count) {
        return InputStats.builder()
                .count(count)
                .p50Millis(p99Millis / 4)
                .p99Millis(p99Millis)
                .maxMillis(p99Millis * 2)
                .build();
    }
}
