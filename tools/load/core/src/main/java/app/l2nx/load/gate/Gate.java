/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.gate.model.CriterionResult;
import app.l2nx.load.gate.model.GateCriterion;
import app.l2nx.load.gate.model.GateThresholds;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compares runs with the baseline on the median of the valid runs, so one outlier cannot decide. A missing meter fails
 * its criterion; engine criteria are not applicable below {@code IN_GAME}, client latency without a baseline.
 */
public final class Gate {

    static final String TIMEOUTS = "timeouts";
    static final String REJECTED = "rejected intents";
    static final String STOPPED_LOAD_BOTS = "stopped load bots";
    static final String NO_BASELINE = "not applicable: no baseline, these runs may become it";

    private static final double NO_OWNERSHIP_VIOLATIONS = 0;

    private Gate() {}

    /**
     * Invalid runs in both lists are ignored; {@code baselines} is empty before a baseline exists.
     *
     * @throws IllegalArgumentException when there is neither a run nor a baseline
     */
    public static GateVerdict evaluate(List<RunSummary> runs, List<RunSummary> baselines, GateThresholds thresholds) {
        var all = Stream.concat(baselines.stream(), runs.stream()).toList();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("The gate needs at least one run");
        }
        var reference = all.getFirst();
        var validBaselines = valid(baselines);
        var incomparable = incomparableReason(all, reference);
        if (incomparable != null) {
            return verdict(
                    reference,
                    all,
                    validBaselines,
                    List.of(),
                    List.of(missing(GateCriterion.COMPARABLE_RUNS, null, null, incomparable)));
        }
        var valid = valid(runs);
        var engineNotApplicable = engineNotApplicableReason(reference);
        var results = new ArrayList<CriterionResult>();
        results.add(CriterionResult.builder()
                .criterion(GateCriterion.VALID_RUNS)
                .measured((double) valid.size())
                .threshold((double) thresholds.runs())
                .passed(valid.size() >= thresholds.runs())
                .build());
        var noValidRuns = "no valid runs";
        if (engineNotApplicable != null) {
            results.add(
                    notApplicable(GateCriterion.ENGINE_PASS_P99, passBudgetMillis(thresholds), engineNotApplicable));
            results.add(notApplicable(
                    GateCriterion.OVER_BUDGET_SHARE, thresholds.maxOverBudgetShare(), engineNotApplicable));
        } else if (valid.isEmpty()) {
            results.add(missing(GateCriterion.ENGINE_PASS_P99, null, passBudgetMillis(thresholds), noValidRuns));
            results.add(missing(GateCriterion.OVER_BUDGET_SHARE, null, thresholds.maxOverBudgetShare(), noValidRuns));
        } else {
            results.addAll(engine(valid, thresholds));
        }
        if (valid.isEmpty()) {
            results.add(missing(GateCriterion.CLIENT_LATENCY_P99, null, null, noValidRuns));
            results.add(missing(
                    GateCriterion.TIMEOUTS_AND_ERRORS, null, thresholds.maxTimeoutAndErrorShare(), noValidRuns));
            results.add(
                    missing(GateCriterion.NOT_AT_FINAL_STATE, null, thresholds.maxNotAtFinalStateShare(), noValidRuns));
            results.add(missing(GateCriterion.OWNERSHIP_VIOLATIONS, null, NO_OWNERSHIP_VIOLATIONS, noValidRuns));
        } else {
            results.addAll(clientLatency(valid, baselines, validBaselines, thresholds));
            results.addAll(timeoutsAndErrors(valid, thresholds));
            results.add(measured(
                    GateCriterion.NOT_AT_FINAL_STATE,
                    null,
                    median(valid, RunSummary::notAtFinalStateShare),
                    thresholds.maxNotAtFinalStateShare()));
            results.addAll(ownershipViolations(valid, engineNotApplicable));
        }
        return verdict(reference, all, validBaselines, valid, results);
    }

    private static List<RunSummary> valid(List<RunSummary> runs) {
        return runs.stream().filter(run -> run.validity() == RunValidity.VALID).toList();
    }

    private static GateVerdict verdict(
            RunSummary reference,
            List<RunSummary> all,
            List<RunSummary> validBaselines,
            List<RunSummary> valid,
            List<CriterionResult> results) {
        return GateVerdict.builder()
                .scenario(reference.scenario())
                .baselineRunIds(runIds(validBaselines.stream()))
                .validRunIds(runIds(valid.stream()))
                .invalidRunIds(runIds(all.stream().filter(run -> run.validity() != RunValidity.VALID)))
                .results(results)
                .build();
    }

    /** {@code null} when every run can be compared with {@code reference} and ran on fresh server processes. */
    private static String incomparableReason(List<RunSummary> all, RunSummary reference) {
        var reasons = new ArrayList<String>();
        addDifference(reasons, all, reference, "scenario", RunSummary::scenario);
        addDifference(reasons, all, reference, "final state", RunSummary::finalState);
        addDifference(reasons, all, reference, "injection", RunSummary::injection);
        addDifference(reasons, all, reference, "protocol", RunSummary::protocol);
        addDifference(reasons, all, reference, "intent timeout", RunSummary::intentTimeout);
        addDifference(reasons, all, reference, "lifecycle timeout", RunSummary::lifecycleTimeout);
        addDifference(reasons, all, reference, "measured phase", RunSummary::measuredPhase);
        addDifference(reasons, all, reference, "environment", RunSummary::environment);
        addDifference(reasons, all, reference, "CPU affinity", RunSummary::cpuAffinity);
        addStaleServers(reasons, all);
        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }

    private static void addDifference(
            List<String> reasons,
            List<RunSummary> all,
            RunSummary reference,
            String name,
            Function<RunSummary, Object> value) {
        var expected = value.apply(reference);
        var other = runIds(all.stream().filter(run -> !Objects.equals(value.apply(run), expected)));
        if (!other.isEmpty()) {
            reasons.add("runs %s differ from %s in the %s (%s)".formatted(other, reference.runId(), name, expected));
        }
    }

    /**
     * A server left running from the previous run keeps its state (accounts still logged in); a run on no server has
     * nothing to check.
     */
    private static void addStaleServers(List<String> reasons, List<RunSummary> all) {
        var measured = all.stream()
                .filter(run -> run.serverWindow() != null)
                .sorted(Comparator.comparing(RunSummary::startedAt))
                .toList();
        var unknown = runIds(measured.stream().filter(run -> startTimes(run).isEmpty()));
        if (!unknown.isEmpty()) {
            reasons.add("runs %s recorded no server start time".formatted(unknown));
        }
        for (int index = 1; index < measured.size(); index++) {
            var run = measured.get(index);
            var previous = measured.get(index - 1);
            var stale = staleJobs(run, previous.startedAt());
            if (!stale.isEmpty()) {
                reasons.add("run %s used the %s servers started before the previous run %s"
                        .formatted(run.runId(), stale, previous.runId()));
            }
        }
    }

    private static List<String> staleJobs(RunSummary run, Instant previousStart) {
        return startTimes(run).entrySet().stream()
                .filter(start -> start.getValue().isBefore(previousStart))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, Instant> startTimes(RunSummary run) {
        return run.serverStartedAt() == null ? Map.of() : run.serverStartedAt();
    }

    /** {@code null} when the load bots play in the game, so the engine criteria apply. */
    private static String engineNotApplicableReason(RunSummary reference) {
        var finalState = reference.finalState();
        if (finalState == LoadBotState.IN_GAME) {
            return null;
        }
        return "not applicable: the load bots stop at %s, before %s, so the engine runs nothing of theirs"
                .formatted(finalState, LoadBotState.IN_GAME);
    }

    private static List<CriterionResult> engine(List<RunSummary> runs, GateThresholds thresholds) {
        var budget = passBudgetMillis(thresholds);
        var maxShare = thresholds.maxOverBudgetShare();
        var withoutLoops = runIds(runs.stream().filter(run -> loops(run).isEmpty()));
        if (!withoutLoops.isEmpty()) {
            var reason = "no engine loop meters in runs " + withoutLoops;
            return List.of(
                    missing(GateCriterion.ENGINE_PASS_P99, null, budget, reason),
                    missing(GateCriterion.OVER_BUDGET_SHARE, null, maxShare, reason));
        }
        var loopNames = new TreeSet<String>();
        runs.forEach(run -> loopNames.addAll(loops(run).keySet()));
        var passP99 = new ArrayList<CriterionResult>();
        var overBudget = new ArrayList<CriterionResult>();
        for (var loop : loopNames) {
            var reason = missingLoopReason(runs, loop);
            if (reason != null) {
                passP99.add(missing(GateCriterion.ENGINE_PASS_P99, loop, budget, reason));
                overBudget.add(missing(GateCriterion.OVER_BUDGET_SHARE, loop, maxShare, reason));
                continue;
            }
            var stats = runs.stream().map(run -> loops(run).get(loop)).toList();
            passP99.add(measured(GateCriterion.ENGINE_PASS_P99, loop, median(stats, LoopStats::passP99Millis), budget));
            overBudget.add(measured(
                    GateCriterion.OVER_BUDGET_SHARE,
                    loop,
                    median(stats, loopStats -> (double) loopStats.overBudget() / loopStats.passes()),
                    maxShare));
        }
        var results = new ArrayList<>(passP99);
        results.addAll(overBudget);
        return results;
    }

    private static String missingLoopReason(List<RunSummary> runs, String loop) {
        var absent = runIds(runs.stream().filter(run -> !loops(run).containsKey(loop)));
        if (!absent.isEmpty()) {
            return "no meters of loop %s in runs %s".formatted(loop, absent);
        }
        var idle = runIds(runs.stream().filter(run -> loops(run).get(loop).passes() == 0));
        if (!idle.isEmpty()) {
            return "no passes of loop %s in runs %s".formatted(loop, idle);
        }
        var withoutP99 = runIds(runs.stream().filter(run -> loops(run).get(loop).passP99Millis() == null));
        if (!withoutP99.isEmpty()) {
            return "no pass p99 of loop %s in runs %s".formatted(loop, withoutP99);
        }
        return null;
    }

    private static List<CriterionResult> ownershipViolations(List<RunSummary> runs, String engineNotApplicable) {
        var withoutLoops = runIds(runs.stream().filter(run -> loops(run).isEmpty()));
        if (!withoutLoops.isEmpty()) {
            return List.of(
                    engineNotApplicable != null
                            ? notApplicable(
                                    GateCriterion.OWNERSHIP_VIOLATIONS, NO_OWNERSHIP_VIOLATIONS, engineNotApplicable)
                            : missing(
                                    GateCriterion.OWNERSHIP_VIOLATIONS,
                                    null,
                                    NO_OWNERSHIP_VIOLATIONS,
                                    "no engine loop meters in runs " + withoutLoops));
        }
        var loopNames = new TreeSet<String>();
        runs.forEach(run -> loopNames.addAll(loops(run).keySet()));
        var results = new ArrayList<CriterionResult>();
        for (var loop : loopNames) {
            var absent = runIds(runs.stream().filter(run -> !loops(run).containsKey(loop)));
            results.add(
                    absent.isEmpty()
                            ? measured(
                                    GateCriterion.OWNERSHIP_VIOLATIONS,
                                    loop,
                                    runs.stream()
                                            .mapToDouble(
                                                    run -> loops(run).get(loop).ownershipViolations())
                                            .max()
                                            .orElseThrow(),
                                    NO_OWNERSHIP_VIOLATIONS)
                            : missing(
                                    GateCriterion.OWNERSHIP_VIOLATIONS,
                                    loop,
                                    NO_OWNERSHIP_VIOLATIONS,
                                    "no meters of loop %s in runs %s".formatted(loop, absent)));
        }
        return results;
    }

    private static List<CriterionResult> clientLatency(
            List<RunSummary> runs,
            List<RunSummary> baselines,
            List<RunSummary> validBaselines,
            GateThresholds thresholds) {
        if (baselines.isEmpty()) {
            return List.of(notApplicable(GateCriterion.CLIENT_LATENCY_P99, null, NO_BASELINE));
        }
        if (validBaselines.size() < thresholds.runs()) {
            return List.of(missing(
                    GateCriterion.CLIENT_LATENCY_P99,
                    null,
                    null,
                    "the baseline has %d valid runs %s of %d needed"
                            .formatted(validBaselines.size(), runIds(validBaselines.stream()), thresholds.runs())));
        }
        var results = new ArrayList<CriterionResult>();
        for (var kind : IntentKind.values()) {
            latency(
                    results,
                    kind.name(),
                    "acknowledged " + kind + " intents",
                    runs,
                    validBaselines,
                    thresholds,
                    run -> {
                        var stats = intents(run).get(kind);
                        return stats == null || stats.acknowledged() == 0 ? null : stats.p99Millis();
                    });
        }
        for (var state : LoadBotState.values()) {
            latency(results, "step " + state, state + " steps", runs, validBaselines, thresholds, run -> {
                var stats = run.steps().get(state);
                return stats == null || stats.count() == 0 ? null : stats.p99Millis();
            });
        }
        return results;
    }

    /** @param p99 of a run, {@code null} when it has no measured sample of the subject */
    private static void latency(
            List<CriterionResult> results,
            String subject,
            String samples,
            List<RunSummary> runs,
            List<RunSummary> baselines,
            GateThresholds thresholds,
            Function<RunSummary, Double> p99) {
        var baselinesWithout = runIds(baselines.stream().filter(run -> p99.apply(run) == null));
        var runsWithout = runIds(runs.stream().filter(run -> p99.apply(run) == null));
        if (baselinesWithout.size() == baselines.size()) {
            if (runsWithout.size() < runs.size()) {
                var present = runIds(runs.stream().filter(run -> p99.apply(run) != null));
                results.add(missing(
                        GateCriterion.CLIENT_LATENCY_P99,
                        subject,
                        null,
                        "no %s in the baseline, present in runs %s".formatted(samples, present)));
            }
            return;
        }
        if (!baselinesWithout.isEmpty()) {
            results.add(missing(
                    GateCriterion.CLIENT_LATENCY_P99,
                    subject,
                    null,
                    "no %s in baseline runs %s".formatted(samples, baselinesWithout)));
            return;
        }
        var threshold = latencyLimit(median(baselines, p99::apply), thresholds);
        if (!runsWithout.isEmpty()) {
            results.add(missing(
                    GateCriterion.CLIENT_LATENCY_P99,
                    subject,
                    threshold,
                    "no %s in runs %s".formatted(samples, runsWithout)));
            return;
        }
        results.add(measured(GateCriterion.CLIENT_LATENCY_P99, subject, median(runs, p99::apply), threshold));
    }

    private static double latencyLimit(double baselineMillis, GateThresholds thresholds) {
        return Math.max(
                baselineMillis * thresholds.maxClientP99Ratio(), baselineMillis + millis(thresholds.clientP99Floor()));
    }

    private static List<CriterionResult> timeoutsAndErrors(List<RunSummary> runs, GateThresholds thresholds) {
        var maxShare = thresholds.maxTimeoutAndErrorShare();
        return List.of(
                measured(GateCriterion.TIMEOUTS_AND_ERRORS, TIMEOUTS, median(runs, RunSummary::timeoutShare), maxShare),
                measured(
                        GateCriterion.TIMEOUTS_AND_ERRORS, REJECTED, median(runs, RunSummary::rejectedShare), maxShare),
                measured(
                        GateCriterion.TIMEOUTS_AND_ERRORS,
                        STOPPED_LOAD_BOTS,
                        median(runs, RunSummary::stoppedShare),
                        maxShare));
    }

    private static CriterionResult measured(GateCriterion criterion, String subject, double value, double threshold) {
        return CriterionResult.builder()
                .criterion(criterion)
                .subject(subject)
                .measured(value)
                .threshold(threshold)
                .passed(value <= threshold)
                .build();
    }

    private static CriterionResult notApplicable(GateCriterion criterion, Double threshold, String reason) {
        return CriterionResult.builder()
                .criterion(criterion)
                .threshold(threshold)
                .passed(true)
                .reason(reason)
                .build();
    }

    private static CriterionResult missing(GateCriterion criterion, String subject, Double threshold, String reason) {
        return CriterionResult.builder()
                .criterion(criterion)
                .subject(subject)
                .threshold(threshold)
                .passed(false)
                .reason(reason)
                .build();
    }

    private static <T> double median(List<T> items, ToDoubleFunction<T> value) {
        var values = items.stream().mapToDouble(value).sorted().toArray();
        var middle = values.length / 2;
        return values.length % 2 == 1 ? values[middle] : (values[middle - 1] + values[middle]) / 2;
    }

    private static double passBudgetMillis(GateThresholds thresholds) {
        return millis(thresholds.passBudget());
    }

    private static double millis(Duration duration) {
        return duration.toNanos() / 1_000_000.0;
    }

    /** Only timed loops: the session drain reports failures and has no passes to judge. */
    private static Map<String, LoopStats> loops(RunSummary run) {
        if (run.server() == null || run.server().loops() == null) {
            return Map.of();
        }
        return run.server().loops().entrySet().stream()
                .filter(loop -> !loop.getValue().untimed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<IntentKind, IntentStats> intents(RunSummary run) {
        return run.intents() == null ? Map.of() : run.intents();
    }

    private static List<String> runIds(Stream<RunSummary> runs) {
        return runs.map(RunSummary::runId).toList();
    }
}
