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
import app.l2nx.highfive.threads.metrics.LagTimedScheduledExecutor;
import app.l2nx.highfive.threads.metrics.LegacyTaskMetrics;
import app.l2nx.highfive.threads.metrics.TaskClassNames;
import app.l2nx.highfive.threads.metrics.ThreadPoolKind;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.search.Search;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * @author Mobius
 */
@Slf4j
public class ThreadPool {

    private static final long ONE_HUNDRED_YEARS_MS = 3155695200000L;
    private static final long MIN_DELAY_MS = 0L;
    private static final long PURGE_INTERVAL_MS = 60000L;
    private static final int INSTANT_POOL_KEEP_ALIVE_MINUTES = 1;
    private static final String EXECUTOR_METER_PREFIX = "executor.";
    private static final String EXECUTOR_NAME_TAG = "name";

    private static volatile ScheduledThreadPoolExecutor HIGH_PRIORITY_SCHEDULED_POOL;
    private static volatile ScheduledThreadPoolExecutor SCHEDULED_POOL;
    private static volatile ThreadPoolExecutor INSTANT_POOL;

    // Written by init on the lifecycle thread, read by every thread that submits a task.
    private static volatile LegacyTaskMetrics taskMetrics;

    private static volatile MeterRegistry meterRegistry;
    private static volatile Set<ThreadPoolKind> boundPools;

    public static void init(MeterRegistry registry) {
        log.info("Initializing.");

        ThreadConfig.load();

        final Set<ThreadPoolKind> pools = EnumSet.of(ThreadPoolKind.SCHEDULED, ThreadPoolKind.INSTANT);
        if (ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE > 0) {
            pools.add(ThreadPoolKind.HIGH_PRIORITY_SCHEDULED);
        }
        taskMetrics = new LegacyTaskMetrics(registry, pools);

        if (ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE > 0) {
            HIGH_PRIORITY_SCHEDULED_POOL = new LagTimedScheduledExecutor(
                    ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE,
                    new ThreadProvider("l2nx High Priority ScheduledThread", ThreadPriority.PRIORITY_8),
                    new ThreadPoolExecutor.CallerRunsPolicy(),
                    taskMetrics.lag(ThreadPoolKind.HIGH_PRIORITY_SCHEDULED));
            log.info(
                    "...scheduled pool executor with {} high priority threads.",
                    ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE);
        }

        SCHEDULED_POOL = new LagTimedScheduledExecutor(
                ThreadConfig.SCHEDULED_THREAD_POOL_SIZE,
                new ThreadProvider("l2nx ScheduledThread"),
                new ThreadPoolExecutor.CallerRunsPolicy(),
                taskMetrics.lag(ThreadPoolKind.SCHEDULED));
        SCHEDULED_POOL.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        SCHEDULED_POOL.setRemoveOnCancelPolicy(true);
        SCHEDULED_POOL.prestartAllCoreThreads();

        INSTANT_POOL = new ThreadPoolExecutor(
                ThreadConfig.INSTANT_THREAD_POOL_SIZE,
                Integer.MAX_VALUE,
                INSTANT_POOL_KEEP_ALIVE_MINUTES,
                TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(),
                new ThreadProvider("l2nx Thread"));
        INSTANT_POOL.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        INSTANT_POOL.prestartAllCoreThreads();

        meterRegistry = registry;
        boundPools = pools;
        new ExecutorServiceMetrics(SCHEDULED_POOL, ThreadPoolKind.SCHEDULED.getTagValue(), Tags.empty())
                .bindTo(registry);
        new ExecutorServiceMetrics(INSTANT_POOL, ThreadPoolKind.INSTANT.getTagValue(), Tags.empty()).bindTo(registry);
        if (HIGH_PRIORITY_SCHEDULED_POOL != null) {
            new ExecutorServiceMetrics(
                            HIGH_PRIORITY_SCHEDULED_POOL,
                            ThreadPoolKind.HIGH_PRIORITY_SCHEDULED.getTagValue(),
                            Tags.empty())
                    .bindTo(registry);
        }

        scheduleAtFixedRate(ThreadPool::purge, PURGE_INTERVAL_MS, PURGE_INTERVAL_MS);

        log.info("...scheduled pool executor with {} total threads.", ThreadConfig.SCHEDULED_THREAD_POOL_SIZE);
        log.info("...instant pool executor with {} total threads.", ThreadConfig.INSTANT_THREAD_POOL_SIZE);
    }

    public static void purge() {
        SCHEDULED_POOL.purge();
        INSTANT_POOL.purge();
        if (HIGH_PRIORITY_SCHEDULED_POOL != null) {
            HIGH_PRIORITY_SCHEDULED_POOL.purge();
        }
    }

    public static ScheduledFuture<?> schedule(Runnable runnable, long delay) {
        try {
            return SCHEDULED_POOL.schedule(
                    new RunnableWrapper(taskMetrics, ThreadPoolKind.SCHEDULED, runnable),
                    validateDelay(delay),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Failed to schedule task {} with delay {}ms: {}{}{}",
                    TaskClassNames.declaringClassName(runnable.getClass()),
                    delay,
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period) {
        try {
            return SCHEDULED_POOL.scheduleAtFixedRate(
                    new RunnableWrapper(taskMetrics, ThreadPoolKind.SCHEDULED, runnable),
                    validateDelay(initialDelay),
                    validateDelay(period),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Failed to schedule recurring task {} with initial delay {}ms and period {}ms: {}{}{}",
                    TaskClassNames.declaringClassName(runnable.getClass()),
                    initialDelay,
                    period,
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public static ScheduledFuture<?> schedulePriorityTaskAtFixedRate(
            Runnable runnable, long initialDelay, long period) {
        if (HIGH_PRIORITY_SCHEDULED_POOL == null) {
            return scheduleAtFixedRate(runnable, initialDelay, period);
        }

        try {
            return HIGH_PRIORITY_SCHEDULED_POOL.scheduleAtFixedRate(
                    new RunnableWrapper(taskMetrics, ThreadPoolKind.HIGH_PRIORITY_SCHEDULED, runnable),
                    validateDelay(initialDelay),
                    validateDelay(period),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Failed to schedule high priority task {} with initial delay {}ms and period {}ms: {}{}{}",
                    TaskClassNames.declaringClassName(runnable.getClass()),
                    initialDelay,
                    period,
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public static void execute(Runnable runnable) {
        try {
            INSTANT_POOL.execute(new RunnableWrapper(taskMetrics, ThreadPoolKind.INSTANT, runnable));
        } catch (Exception e) {
            log.warn(
                    "Failed to execute task {}: {}{}{}",
                    TaskClassNames.declaringClassName(runnable.getClass()),
                    e.getMessage(),
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(e));
        }
    }

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

    public static void shutdown() {
        try {
            log.info("Shutting down all thread pools.");
            if (SCHEDULED_POOL != null) {
                SCHEDULED_POOL.shutdownNow();
            }
            if (INSTANT_POOL != null) {
                INSTANT_POOL.shutdownNow();
            }
            if (HIGH_PRIORITY_SCHEDULED_POOL != null) {
                HIGH_PRIORITY_SCHEDULED_POOL.shutdownNow();
            }
            unbindExecutorMetrics();
        } catch (Throwable t) {
            log.warn("Exception occurred during shutdown: {}", t.getMessage());
        }
    }

    // Re-registering a gauge under the same id returns the old one, still bound to these shut-down pools.
    private static void unbindExecutorMetrics() {
        final MeterRegistry registry = meterRegistry;
        final Set<ThreadPoolKind> pools = boundPools;
        if ((registry == null) || (pools == null)) {
            return;
        }
        for (ThreadPoolKind pool : pools) {
            Search.in(registry)
                    .name(name -> name.startsWith(EXECUTOR_METER_PREFIX))
                    .tag(EXECUTOR_NAME_TAG, pool.getTagValue())
                    .meters()
                    .forEach(registry::remove);
        }
    }

    @Slf4j
    private static class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                return;
            }

            log.warn("Task {} rejected by executor {}, attempting recovery execution.", runnable, executor);

            // A high-priority caller must not be blocked by running the task inline.
            if (Thread.currentThread().getPriority() > Thread.NORM_PRIORITY) {
                new Thread(runnable).start();
            } else {
                runnable.run();
            }
        }
    }
}
