/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.skill;

import lombok.Builder;

/**
 * An effect as stored for an offline owner: game-time durations, so it stands still while nobody plays it.
 *
 * @param remainingMillis the time left, {@code null} for an effect without a duration, such as a toggle
 * @param tickPhaseMillis the time elapsed on the effect's tick schedule; each periodic effect takes it modulo its
 *     own period
 */
@Builder(toBuilder = true)
public record StoredEffect(Long remainingMillis, long tickPhaseMillis) {
    public StoredEffect {
        if ((remainingMillis != null) && (remainingMillis <= 0)) {
            throw new IllegalArgumentException("An expired effect is not stored: " + remainingMillis);
        }

        if (tickPhaseMillis < 0) {
            throw new IllegalArgumentException("Negative tick phase: " + tickPhaseMillis);
        }
    }
}
