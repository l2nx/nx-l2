/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

public enum GateCriterion {
    /** Listed only when it fails, and then alone: runs of another lifecycle are no sample of the baseline. */
    COMPARABLE_RUNS,
    VALID_RUNS,
    /** Together with {@link #OVER_BUDGET_SHARE}, applies only to runs whose final state is {@code IN_GAME}. */
    ENGINE_PASS_P99,
    OVER_BUDGET_SHARE,
    /**
     * Input time p99 of every input class against its baseline; not applicable without a baseline. Client latency of
     * intents is reported, not gated: game timing sets it.
     */
    INPUT_TIME_P99,
    /**
     * Client p99 of every lifecycle step against its baseline; not applicable without a baseline.
     */
    LIFECYCLE_STEP_P99,
    TIMEOUTS_AND_ERRORS,
    NOT_AT_FINAL_STATE,
    OWNERSHIP_VIOLATIONS
}
