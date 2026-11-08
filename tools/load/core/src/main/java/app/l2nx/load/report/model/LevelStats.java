/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.util.Map;
import lombok.Builder;

/**
 * One level of a capacity run, measured like a steady window.
 *
 * @param window {@code null} when the run read no server meters
 * @param passP99Millis the highest engine pass p99 over the timed loops; {@code null} when the level has no meters of
 *     every timed loop
 * @param overBudgetShare the highest share of passes over budget over the timed loops, a fraction; {@code null} like
 *     {@code passP99Millis}
 * @param busyShare by timed loop, a fraction of the level's window
 * @param timeoutShare of the intents whose intended time lies in the level's measured window, as the run's
 * @param rejectedShare like {@code timeoutShare}
 * @param stoppedShare of the level's load bots, those stopped on an error before the level ended
 * @param notAtFinalStateShare of the level's load bots, those that had not reached the final state when it ended
 * @param generatorLagP99Millis {@code null} when no load bot task of the level ran
 * @param valid the generator measured the level: its lag has samples and its p99 is within
 *     {@code max-generator-lag-p99}
 */
@Builder(toBuilder = true)
public record LevelStats(
        int bots,
        ServerWindow window,
        Double passP99Millis,
        Double overBudgetShare,
        Map<String, Double> busyShare,
        Map<String, Double> inputP99Millis,
        double timeoutShare,
        double rejectedShare,
        double stoppedShare,
        double notAtFinalStateShare,
        Double generatorLagP99Millis,
        boolean valid) {
    public LevelStats {
        busyShare = ServerStats.sortedCopy(busyShare);
        inputP99Millis = ServerStats.sortedCopy(inputP99Millis);
    }
}
