/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Map;
import lombok.Builder;

/**
 * Engine meters of one loop over the steady window, in milliseconds. A boxed value is {@code null} when the server
 * does not expose that meter or the window holds no pass.
 */
@Builder(toBuilder = true)
public record LoopStats(
        long passes,
        Double passP50Millis,
        Double passP99Millis,
        Double passMaxMillis,
        long overBudget,
        long failures,
        long timersOverdue,
        Map<String, Double> phaseMeanMillis,
        Double inboxDepthMax,
        Double inboxDepthP99) {}
