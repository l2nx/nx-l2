/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

import java.time.Duration;
import lombok.Builder;

/**
 * Limits of the gate; shares are fractions ({@code 0.001} is 0.1%).
 *
 * @param inputP99Floor added to the baseline p99: sub-millisecond p99 ratios are below the noise of like runs
 * @param stepP99Floor added to the baseline p99: ~1 ms p99 ratios are below the generator lag a valid run may carry
 * @param maxTimeoutAndErrorShare limit of each share: timed-out intents, rejected intents, stopped load bots
 */
@Builder(toBuilder = true)
public record GateThresholds(
        int runs,
        Duration passBudget,
        double maxOverBudgetShare,
        double maxInputP99Ratio,
        Duration inputP99Floor,
        double maxStepP99Ratio,
        Duration stepP99Floor,
        double maxTimeoutAndErrorShare,
        double maxNotAtFinalStateShare) {

    public GateThresholds {
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be at least 1, got " + runs);
        }
        if (passBudget == null || passBudget.isNegative() || passBudget.isZero()) {
            throw new IllegalArgumentException("passBudget must be positive, got " + passBudget);
        }
        requireShare("maxOverBudgetShare", maxOverBudgetShare);
        requireShare("maxTimeoutAndErrorShare", maxTimeoutAndErrorShare);
        requireShare("maxNotAtFinalStateShare", maxNotAtFinalStateShare);
        if (!(maxInputP99Ratio > 0)) {
            throw new IllegalArgumentException("maxInputP99Ratio must be positive, got " + maxInputP99Ratio);
        }
        if (inputP99Floor == null || inputP99Floor.isNegative()) {
            throw new IllegalArgumentException("inputP99Floor must be zero or positive, got " + inputP99Floor);
        }
        if (!(maxStepP99Ratio > 0)) {
            throw new IllegalArgumentException("maxStepP99Ratio must be positive, got " + maxStepP99Ratio);
        }
        if (stepP99Floor == null || stepP99Floor.isNegative()) {
            throw new IllegalArgumentException("stepP99Floor must be zero or positive, got " + stepP99Floor);
        }
    }

    public static GateThresholds defaults() {
        return GateThresholds.builder()
                .runs(5)
                .passBudget(Duration.ofMillis(50))
                .maxOverBudgetShare(0.001)
                .maxInputP99Ratio(1.10)
                .inputP99Floor(Duration.ofNanos(500_000))
                .maxStepP99Ratio(1.10)
                .stepP99Floor(Duration.ofMillis(10))
                .maxTimeoutAndErrorShare(0.005)
                .maxNotAtFinalStateShare(0.005)
                .build();
    }

    private static void requireShare(String name, double share) {
        if (!(share >= 0 && share <= 1)) {
            throw new IllegalArgumentException(name + " must be within [0, 1], got " + share);
        }
    }
}
