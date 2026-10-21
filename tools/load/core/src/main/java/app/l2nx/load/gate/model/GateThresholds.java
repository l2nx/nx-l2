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
 * @param maxClientP99Ratio limit of the client p99 of an intent kind over its baseline p99
 * @param maxTimeoutAndErrorShare limit of each share: timed-out intents, rejected intents, stopped load bots
 */
@Builder(toBuilder = true)
public record GateThresholds(
        int runs,
        Duration passBudget,
        double maxOverBudgetShare,
        double maxClientP99Ratio,
        double maxTimeoutAndErrorShare) {

    public GateThresholds {
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be at least 1, got " + runs);
        }
        if (passBudget == null || passBudget.isNegative() || passBudget.isZero()) {
            throw new IllegalArgumentException("passBudget must be positive, got " + passBudget);
        }
        requireShare("maxOverBudgetShare", maxOverBudgetShare);
        requireShare("maxTimeoutAndErrorShare", maxTimeoutAndErrorShare);
        if (!(maxClientP99Ratio > 0)) {
            throw new IllegalArgumentException("maxClientP99Ratio must be positive, got " + maxClientP99Ratio);
        }
    }

    public static GateThresholds defaults() {
        return GateThresholds.builder()
                .runs(3)
                .passBudget(Duration.ofMillis(50))
                .maxOverBudgetShare(0.001)
                .maxClientP99Ratio(1.10)
                .maxTimeoutAndErrorShare(0.005)
                .build();
    }

    private static void requireShare(String name, double share) {
        if (!(share >= 0 && share <= 1)) {
            throw new IllegalArgumentException(name + " must be within [0, 1], got " + share);
        }
    }
}
