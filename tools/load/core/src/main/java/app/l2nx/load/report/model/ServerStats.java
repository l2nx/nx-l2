/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Map;
import lombok.Builder;

/**
 * Server meters over the measured phase. A boxed value is {@code null} when the server does not expose that meter or
 * the window holds no observation of it.
 *
 * @param legacyMeanMillis legacy timers keyed by their PromQL selector
 * @param legacyP99Millis only timers that publish a histogram have an entry, under the same keys
 * @param legacyMaxMillis histogram timers only: Micrometer's decaying max still holds pre-window observations
 */
@Builder(toBuilder = true)
public record ServerStats(
        Map<String, LoopStats> loops,
        Double sessionQueueDepthP99,
        Double sessionQueueDepthMax,
        Map<String, Double> legacyMeanMillis,
        Map<String, Double> legacyP99Millis,
        Map<String, Double> legacyMaxMillis,
        Double gcPauseP99Millis,
        Double gcPauseMeanMillis,
        Double gcPauseMaxMillis,
        Double processCpuMean,
        Double threadsLiveMax) {}
