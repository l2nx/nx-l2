/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.clock;

import java.time.Clock;

/**
 * Game time and wall-clock time of one simulation owner.
 *
 * <p>Game time is monotonic ms that restart with the process, so it is never persisted. Wall-clock time is epoch
 * time with a zone; values cross only via {@link #wallMillisAt(long)} and {@link #gameMillisAtWall(long)}.
 */
public interface GameClock {
    /**
     * Game time when the clock starts. Not zero: legacy fields start at 0 meaning "never" and are compared as
     * {@code now - last > interval}, which must keep reading as long ago, as it did against epoch time.
     */
    long ORIGIN_MILLIS = 1L << 32;

    long gameMillis();

    Clock wallClock();

    default long wallMillis() {
        return wallClock().millis();
    }

    default long wallMillisAt(long gameMillis) {
        return wallMillis() + (gameMillis - gameMillis());
    }

    default long gameMillisAtWall(long wallMillis) {
        return gameMillis() + (wallMillis - wallMillis());
    }
}
