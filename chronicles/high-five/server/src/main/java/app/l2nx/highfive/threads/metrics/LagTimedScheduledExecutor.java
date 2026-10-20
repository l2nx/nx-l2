/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import io.micrometer.core.instrument.Timer;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Records the start lag of every task against the executor's own trigger time, which for a fixed-rate run is the
 * previous trigger plus the period. A task run by the rejection handler bypasses the workers and records no lag.
 */
public final class LagTimedScheduledExecutor extends ScheduledThreadPoolExecutor {
    private final Timer lag;

    public LagTimedScheduledExecutor(
            int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler, Timer lag) {
        super(corePoolSize, threadFactory, handler);
        this.lag = lag;
    }

    @Override
    protected void beforeExecute(Thread thread, Runnable task) {
        super.beforeExecute(thread, task);
        // A worker only takes the executor's own futures off the queue; the delay is negative once the trigger passed.
        final long delay = ((RunnableScheduledFuture<?>) task).getDelay(TimeUnit.NANOSECONDS);
        lag.record(Math.max(0L, -delay), TimeUnit.NANOSECONDS);
    }
}
