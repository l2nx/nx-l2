/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import java.util.random.RandomGenerator;
import lombok.Builder;

/**
 * Delays of a respawn-like timer re-armed uniformly {@code minMillis} to {@code maxMillis} after each firing. Start a
 * population with {@link #first}, so the fire rate is {@code size / mean} from the start instead of ramping up over
 * {@code maxMillis}.
 */
@Builder(toBuilder = true)
public record RespawnDelay(long minMillis, long maxMillis) {
    public RespawnDelay {
        if (minMillis < 1 || maxMillis < minMillis) {
            throw new IllegalArgumentException(
                    "Respawn delay must be 1 <= min <= max: " + minMillis + ".." + maxMillis);
        }
    }

    public long next(RandomGenerator random) {
        return random.nextLong(minMillis, maxMillis + 1);
    }

    /** Remaining delay of a long-running timer: its interval is picked weighted by length, the present is uniform in it. */
    public long first(RandomGenerator random) {
        final double min = minMillis;
        final double max = maxMillis;
        final double interval = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        return Math.max(1, Math.round(random.nextDouble() * interval));
    }
}
