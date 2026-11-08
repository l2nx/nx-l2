/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import java.time.Duration;
import lombok.Builder;

/**
 * How load bots enter a run: {@code bots} start evenly over {@code ramp}, the warm-up is not reported, the steady
 * window is measured, then the load bots leave over {@code rampDown}. With {@code levels} the levels replace the steady
 * window: {@code bots} is the first level, and each level is measured after its own warm-up.
 *
 * @param levels {@code null} for a run of one level of {@code bots}
 */
@Builder(toBuilder = true)
public record InjectionProfile(
        int bots, Duration ramp, Duration warmUp, Duration steady, Duration rampDown, Levels levels) {

    /** Every load bot of the run, those the levels add included. */
    public int totalBots() {
        return levels == null ? bots : Math.addExact(bots, Math.multiplyExact(levels.increment(), levels.count() - 1));
    }

    /**
     * @param count levels, the first included; level k (0-based) holds {@code bots + k * increment}
     * @param ramp over which the load bots of a level enter after the previous level ends
     */
    @Builder(toBuilder = true)
    public record Levels(int increment, int count, Duration duration, Duration ramp) {
        public Levels {
            if (increment <= 0) {
                throw new IllegalArgumentException("A level must add load bots: " + increment);
            }
            if (count <= 0) {
                throw new IllegalArgumentException("A run with levels needs at least one level: " + count);
            }
            if (duration == null || !duration.isPositive()) {
                throw new IllegalArgumentException("A level must last: " + duration);
            }
            if (ramp == null || ramp.isNegative()) {
                throw new IllegalArgumentException("The ramp between levels must be zero or positive: " + ramp);
            }
        }
    }
}
