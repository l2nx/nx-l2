/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import java.time.Duration;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param passMin minimum distance from a pass to the next one run for queued input, which joins that pass; a due
 *     timer or periodic system runs on time and takes the queued input along
 * @param passBudget a pass longer than this is counted and logged with its phase breakdown
 * @param triggerDepth generations of same-pass work drained in the side-effect phase
 * @param packetsPerPass items one ready input source gives per pass
 * @param overduePerPass timers one wheel runs per pass before the rest waits for the next pass
 * @param owedTicksPerPass ticks one late periodic entry catches up per pass
 * @param blockedPassWarning a pass running longer than this gets the loop thread's stack logged, once per multiple of this duration
 * @param blockedPassExit a pass running longer than this exits the process; {@code null} keeps a blocked loop running
 */
@Builder(toBuilder = true)
@ConfigurationProperties("l2nx.engine")
public record EngineProperties(
        @DefaultValue("5ms") Duration passMin,
        @DefaultValue("50ms") Duration passBudget,
        @DefaultValue("8") int triggerDepth,
        @DefaultValue("16") int packetsPerPass,
        @DefaultValue("4096") int overduePerPass,
        @DefaultValue("10") int owedTicksPerPass,
        @DefaultValue("2s") Duration blockedPassWarning,
        Duration blockedPassExit) {
    public EngineProperties {
        requireMillis("pass-min", passMin);
        requireMillis("pass-budget", passBudget);
        requirePositive("trigger-depth", triggerDepth);
        requirePositive("packets-per-pass", packetsPerPass);
        requirePositive("overdue-per-pass", overduePerPass);
        requirePositive("owed-ticks-per-pass", owedTicksPerPass);
        requireMillis("blocked-pass-warning", blockedPassWarning);
        if (blockedPassExit != null) {
            requireMillis("blocked-pass-exit", blockedPassExit);
            if (blockedPassExit.compareTo(blockedPassWarning) <= 0) {
                throw new IllegalArgumentException("l2nx.engine.blocked-pass-exit must be longer than "
                        + "l2nx.engine.blocked-pass-warning (" + blockedPassWarning + "): " + blockedPassExit);
            }
        }
    }

    public static EngineProperties defaults() {
        return new Binder().bindOrCreate("l2nx.engine", EngineProperties.class);
    }

    // The loop works in whole game milliseconds, so a fraction would be silently cut off.
    private static void requireMillis(String key, Duration value) {
        if (value == null || value.toMillis() < 1 || value.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    "l2nx.engine." + key + " must be a whole number of milliseconds, at least 1 ms: " + value);
        }
    }

    private static void requirePositive(String key, int value) {
        if (value < 1) {
            throw new IllegalArgumentException("l2nx.engine." + key + " must be positive: " + value);
        }
    }
}
