/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.exec;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
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
    private final int maxConcurrency;
    private final MeterRegistry registry;
    private final Timer execution;
    private final Timer idle;
    private final List<Meter> meters;

    /**
     * The active/queued gauges report the semaphore: Micrometer cannot read a thread-per-task executor's thread count.
     * The timers are recorded here, not by {@code ExecutorServiceMetrics.monitor}: {@code idle} must cover the permit
     * wait, and the monitor's wrapper would hide a task that a closing interrupt must route to its rejection path.
     */
    public BlockingTaskExecutor(String name, int maxConcurrency, MeterRegistry registry) {
        final Tags tags = Tags.of("name", name);
        if (registry.find("executor.pool.max").tags(tags).meter() != null) {
            throw new IllegalStateException("A blocking task executor named " + name + " is already registered");
        }
        threads = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(name + "-", 0).factory());
        // Fair, so a lane that hands itself on after a batch queues behind the lanes already waiting.
        permits = new Semaphore(maxConcurrency, true);
        this.maxConcurrency = maxConcurrency;
        this.registry = registry;
        execution = registry.timer("executor.execution", tags);
        idle = registry.timer("executor.idle", tags);
        meters = List.of(
                execution,
                idle,
                Gauge.builder("executor.active", this, BlockingTaskExecutor::activeLanes)
                        .tags(tags)
                        .description("The number of lanes holding a permit")
                        .baseUnit(BaseUnits.THREADS)
                        .register(registry),
                Gauge.builder("executor.queued", this, BlockingTaskExecutor::queuedLanes)
                        .tags(tags)
                        .description("The number of lanes waiting for a permit")
                        .baseUnit(BaseUnits.TASKS)
                        .register(registry),
                Gauge.builder("executor.pool.max", () -> maxConcurrency)
                        .tags(tags)
                        .description("The maximum number of lanes running at once")
                        .baseUnit(BaseUnits.THREADS)
                        .register(registry));
    }

    /**
     * @param rejected receives each accepted task that will not run because the executor closed before it started
     */
    public SerialExecutor newLane(int maxPending, Consumer<Runnable> rejected) {
        return new SerialExecutor(this::start, this::isClosed, maxPending, rejected);
    }

    /**
     * Not an {@link Executor}: an {@code Executor} bean makes Spring Boot drop its application task executor.
     *
     * @param abandoned runs instead of the task, with the interrupt flag set, when the close gives up waiting while the
     *     task still waits for its permit
     * @throws RejectedExecutionException once the executor is closed
     */
    public void execute(Runnable task, Runnable abandoned) {
        // Started before the permit wait, so the idle timer covers it, as Micrometer's timed executor does.
        final Timer.Sample waiting = Timer.start(registry);
        threads.execute(() -> {
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                // Only shutdownNow interrupts.
                Thread.currentThread().interrupt();
                abandoned.run();
                return;
            }
            try {
                waiting.stop(idle);
                execution.record(task);
            } finally {
                permits.release();
            }
        });
    }

    boolean start(Runnable lane, Runnable abandoned) {
        try {
            execute(lane, abandoned);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    boolean isClosed() {
        return threads.isShutdown();
    }

    int activeLanes() {
        return maxConcurrency - permits.availablePermits();
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
        } finally {
            // After the wait, so the last runs are still published.
            meters.forEach(registry::remove);
        }
    }
}
