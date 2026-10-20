/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDK pools configured as the L2J Mobius {@code ThreadPool} configures them, recording their threads so a benchmark can
 * read CPU time.
 */
public final class ClassicThreadPools {
    public static final int SCHEDULED_THREADS = Runtime.getRuntime().availableProcessors() * 4;
    public static final int HIGH_PRIORITY_THREADS = Math.max(2, SCHEDULED_THREADS / 4);
    private static final int HIGH_PRIORITY = 8;

    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();

    public ScheduledThreadPoolExecutor scheduled() {
        return scheduled(SCHEDULED_THREADS);
    }

    public ScheduledThreadPoolExecutor scheduled(int threads) {
        final ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(threads, factory("classic-scheduled", Thread.NORM_PRIORITY));
        pool.setRemoveOnCancelPolicy(true);
        pool.prestartAllCoreThreads();
        return pool;
    }

    public ScheduledThreadPoolExecutor highPriority() {
        return new ScheduledThreadPoolExecutor(HIGH_PRIORITY_THREADS, factory("classic-high", HIGH_PRIORITY));
    }

    /** CPU time of the pool threads alive now; a thread that already ended no longer counts. */
    public long cpuNanos() {
        long total = 0;
        for (Thread thread : threads) {
            final long nanos = threadBean.getThreadCpuTime(thread.threadId());
            if (nanos > 0) {
                total += nanos;
            }
        }
        return total;
    }

    public static void shutdown(ScheduledThreadPoolExecutor pool) throws InterruptedException {
        pool.shutdownNow();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pool threads still running 10 s after shutdown");
        }
    }

    private ThreadFactory factory(String prefix, int priority) {
        final AtomicInteger ids = new AtomicInteger();
        return runnable -> {
            // Daemon, unlike Mobius: a failed teardown must not keep the benchmark fork alive.
            final Thread thread = Thread.ofPlatform()
                    .name(prefix + "-" + ids.incrementAndGet())
                    .priority(priority)
                    .daemon(true)
                    .unstarted(runnable);
            threads.add(thread);
            return thread;
        };
    }
}
