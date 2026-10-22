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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Compares the runs of a scenario with its baseline on the median over the valid runs, so one outlier cannot decide.
 * A missing meter fails its criterion; engine criteria pass as not applicable when load bots stop before
 * {@code IN_GAME}.
 */
public final class Gate {

    static final String TIMEOUTS = "timeouts";
    static final String REJECTED = "rejected intents";
    static final String STOPPED_LOAD_BOTS = "stopped load bots";

    private Gate() {}

    /** @param runs the runs of one scenario and final state, invalid ones included (they are ignored) */
    public static GateVerdict evaluate(List<RunSummary> runs, RunSummary baseline, GateThresholds thresholds) {
        var incomparable = incomparableReason(runs, baseline);
        if (incomparable != null) {
            return verdict(
                    runs,
                    baseline,
                    List.of(),
                    List.of(missing(GateCriterion.COMPARABLE_RUNS, null, null, incomparable)));
        }
        var valid =
                runs.stream().filter(run -> run.validity() == RunValidity.VALID).toList();
        var engineNotApplicable = engineNotApplicableReason(baseline);
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
        } else {
            results.addAll(clientLatency(valid, baseline, thresholds));
            results.addAll(timeoutsAndErrors(valid, thresholds));
        }
        return verdict(runs, baseline, valid, results);
    }

    private static GateVerdict verdict(
            List<RunSummary> runs, RunSummary baseline, List<RunSummary> valid, List<CriterionResult> results) {
        return GateVerdict.builder()
                .scenario(baseline.scenario())
                .baselineRunId(baseline.runId())
                .validRunIds(valid.stream().map(RunSummary::runId).toList())
                .invalidRunIds(runs.stream()
                        .filter(run -> run.validity() != RunValidity.VALID)
                        .map(RunSummary::runId)
                        .toList())
                .results(results)
                .build();
    }

    /** {@code null} when every run is of the baseline's scenario and final state. */
    private static String incomparableReason(List<RunSummary> runs, RunSummary baseline) {
        var reasons = new ArrayList<String>();
        var otherScenario = runIds(runs.stream().filter(run -> !Objects.equals(run.scenario(), baseline.scenario())));
        if (!otherScenario.isEmpty()) {
            reasons.add("runs %s are of another scenario than baseline %s (%s)"
                    .formatted(otherScenario, baseline.runId(), baseline.scenario()));
        }
        var otherFinalState = runIds(runs.stream()
                .filter(run -> Objects.equals(run.scenario(), baseline.scenario())
                        && run.finalState() != baseline.finalState()));
        if (!otherFinalState.isEmpty()) {
            reasons.add("runs %s stop at another final state than baseline %s (%s)"
                    .formatted(otherFinalState, baseline.runId(), baseline.finalState()));
        }
        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }

    /** {@code null} when the load bots play in the game, so the engine criteria apply. */
    private static String engineNotApplicableReason(RunSummary baseline) {
        var finalState = baseline.finalState();
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

    private static List<CriterionResult> clientLatency(
            List<RunSummary> runs, RunSummary baseline, GateThresholds thresholds) {
        if (baseline.validity() != RunValidity.VALID) {
            return List.of(missing(
                    GateCriterion.CLIENT_LATENCY_P99, null, null, "baseline " + baseline.runId() + " is invalid"));
        }
        var baselineIntents = intents(baseline);
        if (baselineIntents.isEmpty()) {
            return List.of(missing(
                    GateCriterion.CLIENT_LATENCY_P99, null, null, "baseline " + baseline.runId() + " has no intents"));
        }
        var results = new ArrayList<CriterionResult>();
        for (var kind : IntentKind.values()) {
            var base = baselineIntents.get(kind);
            if (base == null) {
                var withKind = runIds(runs.stream().filter(run -> intents(run).containsKey(kind)));
                if (!withKind.isEmpty()) {
                    results.add(missing(
                            GateCriterion.CLIENT_LATENCY_P99,
                            kind.name(),
                            null,
                            "no %s intents in baseline %s, present in runs %s"
                                    .formatted(kind, baseline.runId(), withKind)));
                }
                continue;
            }
            if (base.acknowledged() == 0) {
                results.add(missing(
                        GateCriterion.CLIENT_LATENCY_P99,
                        kind.name(),
                        null,
                        "no acknowledged %s intents in baseline %s".formatted(kind, baseline.runId())));
                continue;
            }
            var threshold = base.p99Millis() * thresholds.maxClientP99Ratio();
            var unacknowledged = runIds(runs.stream().filter(run -> {
                var stats = intents(run).get(kind);
                return stats == null || stats.acknowledged() == 0;
            }));
            if (!unacknowledged.isEmpty()) {
                results.add(missing(
                        GateCriterion.CLIENT_LATENCY_P99,
                        kind.name(),
                        threshold,
                        "no acknowledged %s intents in runs %s".formatted(kind, unacknowledged)));
                continue;
            }
            var stats = runs.stream().map(run -> intents(run).get(kind)).toList();
            results.add(measured(
                    GateCriterion.CLIENT_LATENCY_P99, kind.name(), median(stats, IntentStats::p99Millis), threshold));
        }
        return results;
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
        return thresholds.passBudget().toNanos() / 1_000_000.0;
    }

    private static Map<String, LoopStats> loops(RunSummary run) {
        return run.server() == null || run.server().loops() == null
                ? Map.of()
                : run.server().loops();
    }

    private static Map<IntentKind, IntentStats> intents(RunSummary run) {
        return run.intents() == null ? Map.of() : run.intents();
    }

    private static List<String> runIds(Stream<RunSummary> runs) {
        return runs.map(RunSummary::runId).toList();
    }
}
