/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.clock;

import java.time.Clock;
import java.util.Objects;

public final class SystemGameClock implements GameClock {
    private final long originNanos = System.nanoTime();
    private final Clock wallClock;

    public SystemGameClock(Clock wallClock) {
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
    }

    @Override
    public long gameMillis() {
        return ORIGIN_MILLIS + (System.nanoTime() - originNanos) / 1_000_000;
    }

    @Override
    public Clock wallClock() {
        return wallClock;
    }
}
