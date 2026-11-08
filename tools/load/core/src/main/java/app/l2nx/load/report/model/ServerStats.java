/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Collection;
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
 * @param inputs by input class ({@code type} tag of {@code l2nx.session.input}), only classes that ran in the window
 */
@Builder(toBuilder = true)
public record ServerStats(
        Map<String, LoopStats> loops,
        Double sessionQueueDepthP99,
        Double sessionQueueDepthMax,
        Map<String, Double> legacyPacketMeanMillis,
        Map<String, ProcessStats> processes,
        Map<String, InputStats> inputs) {
    public ServerStats {
        loops = sortedCopy(loops);
        legacyPacketMeanMillis = sortedCopy(legacyPacketMeanMillis);
        processes = sortedCopy(processes);
        inputs = sortedCopy(inputs);
    }

    /** Only loops with a pass timer: the session drain reports failures and has no passes to judge. */
    public Map<String, LoopStats> timedLoops() {
        if (loops == null) {
            return Map.of();
        }
        final TreeMap<String, LoopStats> timed = new TreeMap<>();
        loops.forEach((name, loop) -> {
            if (!loop.untimed()) {
                timed.put(name, loop);
            }
        });
        return Collections.unmodifiableSortedMap(timed);
    }

    /**
     * The highest engine pass p99 over the timed loops; {@code null} without a timed loop or when one of them has no
     * pass or no p99, since a loop without a judgement leaves the whole one open.
     */
    public Double worstPassP99Millis() {
        final Collection<LoopStats> timed = timedLoops().values();
        if (timed.isEmpty() || timed.stream().anyMatch(loop -> loop.passes() == 0 || loop.passP99Millis() == null)) {
            return null;
        }
        return timed.stream().mapToDouble(LoopStats::passP99Millis).max().orElseThrow();
    }

    /** The highest share of passes over budget over the timed loops; {@code null} like {@link #worstPassP99Millis}. */
    public Double worstOverBudgetShare() {
        final Collection<LoopStats> timed = timedLoops().values();
        if (timed.isEmpty() || timed.stream().anyMatch(loop -> loop.overBudgetShare() == null)) {
            return null;
        }
        return timed.stream().mapToDouble(LoopStats::overBudgetShare).max().orElseThrow();
    }

    /** Sorted, so the report lists loops, jobs, phases and packet classes by name. */
    static <V> Map<String, V> sortedCopy(Map<String, V> values) {
        return values == null ? null : Collections.unmodifiableSortedMap(new TreeMap<>(values));
    }
}
