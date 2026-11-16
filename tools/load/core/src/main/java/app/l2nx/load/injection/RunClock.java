/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The monotonic clock of a run in nanoseconds, and how the coordinating thread waits on it. The defaults wait in real
 * time; a test clock in virtual time overrides them to run the event loops up to the deadline instead.
 */
@FunctionalInterface
public interface RunClock {
    RunClock SYSTEM = System::nanoTime;

    long now();

    default void sleepUntil(long time) throws InterruptedException {
        final long delay = time - now();
        if (delay > 0) {
            TimeUnit.NANOSECONDS.sleep(delay);
        }
    }

    /** @return whether the latch reached zero by {@code deadline} */
    default boolean await(CountDownLatch latch, long deadline) throws InterruptedException {
        return latch.await(Math.max(0, deadline - now()), TimeUnit.NANOSECONDS);
    }
}
