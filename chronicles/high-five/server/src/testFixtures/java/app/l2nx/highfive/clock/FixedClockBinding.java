/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import app.l2nx.engine.core.clock.GameClock;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Binds {@link ClockBridge} to a fixed clock that answers on any thread, through its package-private binding methods,
 * for tests and benchmarks that call legacy code outside a running pass.
 */
public final class FixedClockBinding implements AutoCloseable {
    private final ClockBridge.TimeSource previous;

    private FixedClockBinding(ClockBridge.TimeSource previous) {
        this.previous = previous;
    }

    public static FixedClockBinding bind(GameClock clock, Supplier<? extends RandomGenerator> random) {
        return new FixedClockBinding(ClockBridge.swap(new ClockBridge.FixedTimeSource(clock, random)));
    }

    @Override
    public void close() {
        ClockBridge.swap(previous);
    }
}
