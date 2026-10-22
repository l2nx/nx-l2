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
    CLIENT_LATENCY_P99,
    TIMEOUTS_AND_ERRORS
}
