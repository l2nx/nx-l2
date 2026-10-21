/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

/** A check of the gate, in the order the verdict lists them. */
public enum GateCriterion {
    /** Enough valid runs to take a median from; an invalid run does not count. */
    VALID_RUNS,
    /** {@code l2nx.engine.pass} p99 over the steady window, per loop, within the pass budget. */
    ENGINE_PASS_P99,
    OVER_BUDGET_SHARE,
    CLIENT_LATENCY_P99,
    /** Timed-out intents, rejected intents and load bots stopped on an error, each share on its own. */
    TIMEOUTS_AND_ERRORS
}
