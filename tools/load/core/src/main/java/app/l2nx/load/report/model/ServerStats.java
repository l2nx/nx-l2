/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Map;
import lombok.Builder;

/**
 * Server meters over the steady window. A boxed value is {@code null} when the server does not expose that meter or
 * the window holds no observation of it.
 *
 * @param loops engine meters by the {@code loop} tag
 * @param legacyMeanMillis mean of the legacy task, task lag and packet timers, keyed by their PromQL selector
 * @param legacyP99Millis p99 of the legacy timers that publish a histogram (task lag, all tasks), same keys; a timer
 *     without a histogram has no entry
 * @param legacyMaxMillis max of the same histogram timers, bounded by the window's buckets; a timer without a
 *     histogram has no entry, since Micrometer's decaying max still holds observations from before the window
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
