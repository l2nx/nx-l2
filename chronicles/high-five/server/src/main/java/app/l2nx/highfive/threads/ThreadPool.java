/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.threads;

import app.l2nx.highfive.config.ThreadConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Provides methods to schedule tasks with delays, fixed rates and immediate execution.<br>
 * Manages multiple thread pools including scheduled tasks, instant execution and high priority scheduling.
 * <ul>
 * <li>Scheduled thread pool for delayed and recurring tasks.</li>
 * <li>Instant thread pool for immediate task execution.</li>
 * <li>High priority scheduled thread pool for critical tasks.</li>
 * <li>Automatic task purging and cleanup mechanisms.</li>
 * </ul>
 * @author Mobius
 */
@Slf4j
public class ThreadPool {

    // Constants.
    private static final long ONE_HUNDRED_YEARS_MS = 3155695200000L;
    private static final long MIN_DELAY_MS = 0L;
    private static final long PURGE_INTERVAL_MS = 60000L;
    private static final int INSTANT_POOL_KEEP_ALIVE_MINUTES = 1;

    // Thread Pool Executors.
    private static ScheduledThreadPoolExecutor HIGH_PRIORITY_SCHEDULED_POOL;
    private static ScheduledThreadPoolExecutor SCHEDULED_POOL;
    private static ThreadPoolExecutor INSTANT_POOL;

    /**
     * Initializes thread pool executors and starts maintenance tasks.
     */
    public static void init() {
        log.info("Initializing.");

        // Load configurations.
        ThreadConfig.load();

        // Configure High Priority ScheduledThreadPoolExecutor.
        if (ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE > 0) {
            HIGH_PRIORITY_SCHEDULED_POOL = new ScheduledThreadPoolExecutor(
                    ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE,
                    new ThreadProvider("l2nx High Priority ScheduledThread", ThreadPriority.PRIORITY_8),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            log.info(
                    "...scheduled pool executor with {} high priority threads.",
                    ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE);
        }

        // Configure ScheduledThreadPoolExecutor.
        SCHEDULED_POOL = new ScheduledThreadPoolExecutor(
                ThreadConfig.SCHEDULED_THREAD_POOL_SIZE,
                new ThreadProvider("l2nx ScheduledThread"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        SCHEDULED_POOL.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        SCHEDULED_POOL.setRemoveOnCancelPolicy(true);
        SCHEDULED_POOL.prestartAllCoreThreads();

        // Configure ThreadPoolExecutor.
        INSTANT_POOL = new ThreadPoolExecutor(
                ThreadConfig.INSTANT_THREAD_POOL_SIZE,
                Integer.MAX_VALUE,
                INSTANT_POOL_KEEP_ALIVE_MINUTES,
                TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(),
                new ThreadProvider("l2nx Thread"));
        INSTANT_POOL.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        INSTANT_POOL.prestartAllCoreThreads();

        // Schedule the purge task.
        scheduleAtFixedRate(ThreadPool::purge, PURGE_INTERVAL_MS, PURGE_INTERVAL_MS);

        // Log thread pool configuration.
        log.info("...scheduled pool executor with {} total threads.", ThreadConfig.SCHEDULED_THREAD_POOL_SIZE);
        log.info("...instant pool executor with {} total threads.", ThreadConfig.INSTANT_THREAD_POOL_SIZE);
    }

    /**
     * Call once, after {@link #init()}: the pools do not exist before.
     */
    public static void bindMetrics(MeterRegistry registry) {
        new ExecutorServiceMetrics(SCHEDULED_POOL, "l2nx-scheduled", Tags.empty()).bindTo(registry);
        new ExecutorServiceMetrics(INSTANT_POOL, "l2nx-instant", Tags.empty()).bindTo(registry);
        if (HIGH_PRIORITY_SCHEDULED_POOL != null) {
            new ExecutorServiceMetrics(HIGH_PRIORITY_SCHEDULED_POOL, "l2nx-high-priority-scheduled", Tags.empty())
                    .bindTo(registry);
        }
    }

    /**
     * Purges cancelled tasks from all thread pools to free memory.
     */
    public static void purge() {
        SCHEDULED_POOL.purge();
        INSTANT_POOL.purge();
        if (HIGH_PRIORITY_SCHEDULED_POOL != null) {
            HIGH_PRIORITY_SCHEDULED_POOL.purge();
        }
    }

    /**
     * Creates and executes a one-shot action that becomes enabled after the given delay.
     * @param runnable the task to execute
     * @param delay the time from now to delay execution
     * @return a ScheduledFuture representing pending completion of the task and whose get() method will return null upon completion
     */
    public static ScheduledFuture<?> schedule(Runnable runnable, long delay) {
        try {
            return SCHEDULED_POOL.schedule(new RunnableWrapper(runnable), validateDelay(delay), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Failed to schedule task {} with delay {}ms: {}{}{}",
                    runnable.getClass().getSimpleName(),
                    delay,
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    /**
     * Creates and executes a periodic action that becomes enabled first after the given initial delay.
     * @param runnable the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @return a ScheduledFuture representing pending completion of the task and whose get() method will throw an exception upon cancellation
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period) {
        try {
            return SCHEDULED_POOL.scheduleAtFixedRate(
                    new RunnableWrapper(runnable),
                    validateDelay(initialDelay),
                    validateDelay(period),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Failed to schedule recurring task {} with initial delay {}ms and period {}ms: {}{}{}",
                    runnable.getClass().getSimpleName(),
                    initialDelay,
                    period,
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    /**
     * Creates and executes a periodic action using high priority thread pool.<br>
     * Designed for tasks requiring immediate or high-priority execution.
     * @param runnable the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @return a ScheduledFuture representing pending completion of the task and whose get() method will throw an exception upon cancellation
     */
    public static ScheduledFuture<?> schedulePriorityTaskAtFixedRate(
            Runnable runnable, long initialDelay, long period) {
        if (HIGH_PRIORITY_SCHEDULED_POOL == null) {
            return scheduleAtFixedRate(runnable, initialDelay, period);
        }

        try {
            return HIGH_PRIORITY_SCHEDULED_POOL.scheduleAtFixedRate(
                    new RunnableWrapper(runnable),
                    validateDelay(initialDelay),
                    validateDelay(period),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Failed to schedule high priority task {} with initial delay {}ms and period {}ms: {}{}{}",
                    runnable.getClass().getSimpleName(),
                    initialDelay,
                    period,
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    /**
     * Executes the given task sometime in the future using the instant thread pool.
     * @param runnable the task to execute
     */
    public static void execute(Runnable runnable) {
        try {
            INSTANT_POOL.execute(new RunnableWrapper(runnable));
        } catch (Exception e) {
            log.warn(
                    "Failed to execute task {}: {}{}{}",
                    runnable.getClass().getSimpleName(),
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Validates delay value to ensure it falls within acceptable bounds.
     * @param delay the delay to validate
     * @return a valid delay value between MIN_DELAY_MS and ONE_HUNDRED_YEARS_MS
     */
    private static long validateDelay(long delay) {
        if (delay < MIN_DELAY_MS) {
            log.warn("Invalid delay {}ms is below minimum, using {}ms instead.", delay, MIN_DELAY_MS, new Exception());
            return MIN_DELAY_MS;
        }

        if (delay > ONE_HUNDRED_YEARS_MS) {
            log.warn(
                    "Invalid delay {}ms exceeds maximum, using {}ms instead.",
                    delay,
                    ONE_HUNDRED_YEARS_MS,
                    new Exception());
            return ONE_HUNDRED_YEARS_MS;
        }

        return delay;
    }

    /**
     * Shutdown thread pooling system correctly.
     */
    public static void shutdown() {
        try {
            log.info("Shutting down all thread pools.");
            SCHEDULED_POOL.shutdownNow();
            INSTANT_POOL.shutdownNow();
            if (HIGH_PRIORITY_SCHEDULED_POOL != null) {
                HIGH_PRIORITY_SCHEDULED_POOL.shutdownNow();
            }
        } catch (Throwable t) {
            log.warn("Exception occurred during shutdown: {}", t.getMessage());
        }
    }

    /**
     * Handles tasks rejected by ThreadPoolExecutor by running them in new thread or current thread.<br>
     * Decision based on current thread priority to prevent blocking high priority operations.
     */
    @Slf4j
    private static class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                return;
            }

            log.warn(
                    "Task {} rejected by executor {}, attempting recovery execution.",
                    runnable.getClass().getSimpleName(),
                    executor);

            // Run in new thread for high priority contexts, current thread otherwise.
            if (Thread.currentThread().getPriority() > Thread.NORM_PRIORITY) {
                new Thread(runnable).start();
            } else {
                runnable.run();
            }
        }
    }

    /**
     * Wraps a Runnable to handle uncaught exceptions during execution.<br>
     * Passes exceptions to the thread's uncaught exception handler for proper error management.
     */
    private static class RunnableWrapper implements Runnable {
        private final Runnable wrappedRunnable;

        /**
         * Creates a new RunnableWrapper for the specified runnable.
         * @param runnable the runnable to wrap
         */
        public RunnableWrapper(Runnable runnable) {
            wrappedRunnable = runnable;
        }

        @Override
        public void run() {
            try {
                wrappedRunnable.run();
            } catch (Throwable t) {
                final Thread currentThread = Thread.currentThread();
                final UncaughtExceptionHandler exceptionHandler = currentThread.getUncaughtExceptionHandler();
                if (exceptionHandler != null) {
                    exceptionHandler.uncaughtException(currentThread, t);
                }
            }
        }
    }
}
