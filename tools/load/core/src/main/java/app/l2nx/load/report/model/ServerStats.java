/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.Builder;

/**
 * Server meters over the measured phase. A boxed value is {@code null} when the server does not expose that meter or
 * the window holds no observation of it.
 *
 * @param processes by Prometheus job, such as {@code game} and {@code login}
 * @param legacyPacketMeanMillis by packet class ({@code type} tag)
 */
@Builder(toBuilder = true)
public record ServerStats(
        Map<String, LoopStats> loops,
        Double sessionQueueDepthP99,
        Double sessionQueueDepthMax,
        Map<String, Double> legacyPacketMeanMillis,
        Map<String, ProcessStats> processes) {
    public ServerStats {
        loops = sortedCopy(loops);
        legacyPacketMeanMillis = sortedCopy(legacyPacketMeanMillis);
        processes = sortedCopy(processes);
    }

    /** Sorted, so the report lists loops, jobs, phases and packet classes by name. */
    static <V> Map<String, V> sortedCopy(Map<String, V> values) {
        return values == null ? null : Collections.unmodifiableSortedMap(new TreeMap<>(values));
    }
}
