/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import java.time.Duration;
import java.util.random.RandomGenerator;
import lombok.Builder;

/**
 * When a load bot acts: every {@code interval} plus a uniform jitter in {@code [-jitter, +jitter]}. The next intended
 * time follows the previous intended time, never the actual send time, so the rate stays open.
 *
 * @param jitter zero or positive and below {@code interval}, so intended times always move forward
 */
@Builder(toBuilder = true)
public record ActionSchedule(Duration interval, Duration jitter) {
    public ActionSchedule {
        if (interval == null || !interval.isPositive()) {
            throw new IllegalArgumentException("The action interval must be positive: " + interval);
        }
        if (jitter == null || jitter.isNegative() || jitter.compareTo(interval) >= 0) {
            throw new IllegalArgumentException(
                    "The jitter must be zero or positive and below the interval " + interval + ": " + jitter);
        }
    }

    public long firstDelayNanos(RandomGenerator random) {
        return random.nextLong(interval.toNanos());
    }

    public long nextDelayNanos(RandomGenerator random) {
        final long jitterNanos = jitter.toNanos();
        final long offset = jitterNanos == 0 ? 0 : random.nextLong(-jitterNanos, jitterNanos + 1);
        return interval.toNanos() + offset;
    }
}
