/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Map;
import lombok.Builder;

/**
 * A boxed value is {@code null} when the server does not expose that meter or the window holds no pass.
 *
 * @param untimed the loop has failures but no pass timer, such as the session drain: its zero passes say nothing, unlike
 *     those of a timed loop that stood still
 * @param jdbcOnLoopShare of the pass time, a fraction
 * @param jdbcReads statements of the kind loop threads ran; {@code null} in a report written before the column
 * @param busyShare pass time over the window, a fraction
 */
@Builder(toBuilder = true)
public record LoopStats(
        boolean untimed,
        long passes,
        Double passP50Millis,
        Double passP99Millis,
        Double passMaxMillis,
        long overBudget,
        long failures,
        long timersOverdue,
        long ownershipViolations,
        long jdbcOnLoop,
        Double jdbcOnLoopSeconds,
        Double jdbcOnLoopShare,
        Long jdbcReads,
        Long jdbcWrites,
        Map<String, Double> phaseMeanMillis,
        Double inboxDepthMax,
        Double inboxDepthP99,
        Double busyShare) {
    public LoopStats {
        phaseMeanMillis = ServerStats.sortedCopy(phaseMeanMillis);
    }

    /** Passes over budget over all passes, a fraction; {@code null} without a pass. */
    public Double overBudgetShare() {
        return passes == 0 ? null : (double) overBudget / passes;
    }
}
