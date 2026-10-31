/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.injection.InjectionProfile;
import java.time.Duration;
import lombok.Builder;

/**
 * @param intentTimeout an intent not acknowledged within it from its intended time counts as a timeout
 */
@Builder(toBuilder = true)
public record RunSettings(
        String scenario,
        String protocol,
        InjectionProfile injection,
        long seed,
        int eventLoopThreads,
        Duration intentTimeout,
        Duration maxGeneratorLagP99,
        double maxStoppedShare,
        String serverCommit,
        String cpuAffinity) {
    public RunSettings {
        requireText("scenario", scenario);
        requireText("protocol", protocol);
        if (injection == null) {
            throw new IllegalArgumentException("The injection profile is required");
        }
        if (eventLoopThreads <= 0) {
            throw new IllegalArgumentException("Event loop threads must be positive: " + eventLoopThreads);
        }
        requirePositive("intent timeout", intentTimeout);
        requirePositive("maximum generator lag p99", maxGeneratorLagP99);
        if (!(maxStoppedShare >= 0 && maxStoppedShare <= 1)) {
            throw new IllegalArgumentException("The maximum stopped share must be within [0, 1]: " + maxStoppedShare);
        }
        if (serverCommit != null) {
            requireText("server commit", serverCommit);
        }
        if (cpuAffinity != null) {
            requireText("CPU affinity", cpuAffinity);
        }
    }

    public static RunSettingsBuilder withDefaults() {
        return builder()
                .eventLoopThreads(Runtime.getRuntime().availableProcessors())
                .intentTimeout(Duration.ofSeconds(5))
                .maxGeneratorLagP99(Duration.ofMillis(10))
                .maxStoppedShare(0.01);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + name + " must not be blank: " + value);
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || !value.isPositive()) {
            throw new IllegalArgumentException("The " + name + " must be positive: " + value);
        }
    }
}
