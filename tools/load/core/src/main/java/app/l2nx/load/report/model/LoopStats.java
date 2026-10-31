/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Map;
import lombok.Builder;

/** A boxed value is {@code null} when the server does not expose that meter or the window holds no pass. */
@Builder(toBuilder = true)
public record LoopStats(
        long passes,
        Double passP50Millis,
        Double passP99Millis,
        Double passMaxMillis,
        long overBudget,
        long failures,
        long timersOverdue,
        long jdbcOnLoop,
        Map<String, Double> phaseMeanMillis,
        Double inboxDepthMax,
        Double inboxDepthP99) {
    public LoopStats {
        phaseMeanMillis = ServerStats.sortedCopy(phaseMeanMillis);
    }
}
