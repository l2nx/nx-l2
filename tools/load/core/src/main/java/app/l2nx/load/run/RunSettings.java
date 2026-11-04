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
 * @param lifecycleTimeout a load bot that stays this long in a state before its final state stops on an error; not
 *     below {@code intentTimeout}, or a lifecycle intent would fail as an error before it could time out
 */
@Builder(toBuilder = true)
public record RunSettings(
        String scenario,
        String protocol,
        InjectionProfile injection,
        long seed,
        int eventLoopThreads,
        Duration intentTimeout,
        Duration lifecycleTimeout,
        Duration maxGeneratorLagP99,
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
        requirePositive("lifecycle timeout", lifecycleTimeout);
        if (lifecycleTimeout.compareTo(intentTimeout) < 0) {
            throw new IllegalArgumentException("The lifecycle timeout " + lifecycleTimeout
                    + " must not be below the intent timeout " + intentTimeout);
        }
        requirePositive("maximum generator lag p99", maxGeneratorLagP99);
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
                .lifecycleTimeout(Duration.ofSeconds(30))
                .maxGeneratorLagP99(Duration.ofMillis(10));
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
