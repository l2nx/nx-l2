/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.report.model.Capacity;
import app.l2nx.load.report.model.CapacityLimit;
import app.l2nx.load.report.model.LevelStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Judges the levels of a run against the gate settings into its {@link Capacity}. */
final class CapacityJudge {
    private CapacityJudge() {}

    /**
     * @param levels in the order they ran
     * @param maxTimeoutAndErrorShare limit of each share: timed-out intents, rejected intents, stopped load bots
     */
    static Capacity judge(
            List<LevelStats> levels,
            Duration passBudget,
            double maxOverBudgetShare,
            double maxTimeoutAndErrorShare,
            double maxNotAtFinalStateShare) {
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("A capacity needs at least one level");
        }
        final double budgetMillis = passBudget.toNanos() / 1_000_000.0;
        Integer passed = null;
        for (LevelStats level : levels) {
            if (!level.valid()) {
                return stop(passed, CapacityLimit.GENERATOR, invalidReason(level));
            }
            final List<String> failed = failedShares(level, maxTimeoutAndErrorShare, maxNotAtFinalStateShare);
            if (!failed.isEmpty()) {
                return stop(
                        passed,
                        CapacityLimit.LOAD_BOTS_FAILED,
                        "level %d: %s".formatted(level.bots(), String.join(", ", failed)));
            }
            if (level.passP99Millis() == null || level.overBudgetShare() == null) {
                return stop(
                        passed,
                        CapacityLimit.SERVER_METERS,
                        "level %d has no engine pass meters".formatted(level.bots()));
            }
            if (level.passP99Millis() > budgetMillis || level.overBudgetShare() > maxOverBudgetShare) {
                return stop(
                        passed,
                        CapacityLimit.PASS_BUDGET,
                        String.format(
                                Locale.ROOT,
                                "level %d: pass p99 %.3f ms, over budget %.4f%%",
                                level.bots(),
                                level.passP99Millis(),
                                level.overBudgetShare() * 100));
            }
            passed = level.bots();
        }
        return Capacity.builder()
                .bots(passed)
                .limitedBy(CapacityLimit.LAST_LEVEL)
                .reason("every level passed; the capacity is at least the last level")
                .build();
    }

    private static String invalidReason(LevelStats level) {
        if (level.generatorLagP99Millis() == null) {
            return "level %d is invalid: no generator lag sample".formatted(level.bots());
        }
        return String.format(
                Locale.ROOT,
                "level %d is invalid: generator lag p99 %.2f ms",
                level.bots(),
                level.generatorLagP99Millis());
    }

    private static List<String> failedShares(
            LevelStats level, double maxTimeoutAndErrorShare, double maxNotAtFinalStateShare) {
        final List<String> failed = new ArrayList<>();
        addOver(failed, "timeouts", level.timeoutShare(), maxTimeoutAndErrorShare);
        addOver(failed, "rejected", level.rejectedShare(), maxTimeoutAndErrorShare);
        addOver(failed, "stopped load bots", level.stoppedShare(), maxTimeoutAndErrorShare);
        addOver(failed, "not at the final state", level.notAtFinalStateShare(), maxNotAtFinalStateShare);
        return failed;
    }

    private static void addOver(List<String> failed, String name, double share, double limit) {
        if (share > limit) {
            failed.add(String.format(Locale.ROOT, "%s %.3f%% over %.3f%%", name, share * 100, limit * 100));
        }
    }

    private static Capacity stop(Integer passed, CapacityLimit limit, String why) {
        return Capacity.builder()
                .bots(passed)
                .limitedBy(limit)
                .reason(passed == null ? "no level passed: " + why : why)
                .build();
    }
}
