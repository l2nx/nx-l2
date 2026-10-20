/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.exec;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Blocking work (RSA, database) off the IO threads. Virtual threads make waiting cheap; the semaphore bounds how many
 * lanes run at once, so a login burst cannot exhaust the connection pool.
 */
@Slf4j
public final class BlockingTaskExecutor implements AutoCloseable {
    private static final Duration CLOSE_WAIT = Duration.ofSeconds(10);

    private final ExecutorService threads;
    private final Semaphore permits;

    public BlockingTaskExecutor(String name, int maxConcurrency, MeterRegistry registry) {
        threads = ExecutorServiceMetrics.monitor(
                registry,
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name(name + "-", 0).factory()),
                name);
        // Fair, so a lane that hands itself on after a batch queues behind the lanes already waiting.
        permits = new Semaphore(maxConcurrency, true);
    }

    /**
     * @param rejected receives each accepted task that will not run because the executor closed before it started
     */
    public SerialExecutor newLane(int maxPending, Consumer<Runnable> rejected) {
        return new SerialExecutor(this::start, this::isClosed, maxPending, rejected);
    }

    boolean start(Runnable lane) {
        try {
            threads.execute(() -> {
                try {
                    permits.acquire();
                } catch (InterruptedException e) {
                    // Only shutdownNow interrupts: the lane sees the executor closed and rejects instead of running.
                    Thread.currentThread().interrupt();
                    lane.run();
                    return;
                }
                try {
                    lane.run();
                } finally {
                    permits.release();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    boolean isClosed() {
        return threads.isShutdown();
    }

    int queuedLanes() {
        return permits.getQueueLength();
    }

    @Override
    public void close() {
        threads.shutdown();
        try {
            if (!threads.awaitTermination(CLOSE_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Blocking tasks still running after {}", CLOSE_WAIT);
                threads.shutdownNow();
            }
        } catch (InterruptedException e) {
            threads.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
