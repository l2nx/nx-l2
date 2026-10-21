/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.exec;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jctools.queues.MpscArrayQueue;

/**
 * One connection's lane: tasks run in submission order, never two at once. {@code running} is cleared before the
 * queue is re-checked, so no task is stranded; only its holder polls the queue.
 */
@Slf4j
public final class SerialExecutor {
    static final int DRAIN_BATCH = 16;

    private final Starter start;
    private final BooleanSupplier closed;
    private final int maxPending;
    private final Consumer<Runnable> rejected;
    // The capacity rounds up to a power of two; offerIfBelowThreshold keeps the limit exact.
    private final MpscArrayQueue<Runnable> tasks;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Runnable drain = this::drain;
    private final Runnable rejectQueued = this::rejectQueued;

    @FunctionalInterface
    interface Starter {
        /**
         * @param abandoned runs instead of {@code drain} when the executor closes before the drain gets a permit
         * @return {@code false} once the executor is shut down
         */
        boolean start(Runnable drain, Runnable abandoned);
    }

    SerialExecutor(Starter start, BooleanSupplier closed, int maxPending, Consumer<Runnable> rejected) {
        this.start = start;
        this.closed = closed;
        this.maxPending = maxPending;
        this.rejected = rejected;
        tasks = new MpscArrayQueue<>(Math.max(2, maxPending));
    }

    /**
     * @return {@code false} when the task is not accepted: {@code maxPending} tasks wait or the executor is closed. An
     *     accepted task runs, or goes to the lane's rejection handler if the executor closes before it can start.
     */
    public boolean execute(Runnable task) {
        if (closed.getAsBoolean() || !tasks.offerIfBelowThreshold(task, maxPending)) {
            return false;
        }
        schedule();
        return true;
    }

    /** Tells a refused task of a stopping executor from one of a full lane. */
    public boolean isClosed() {
        return closed.getAsBoolean();
    }

    private void schedule() {
        // The start fails only once the executor is shut down, so no later start can succeed either.
        if (running.compareAndSet(false, true) && !start.start(drain, rejectQueued)) {
            rejectQueued();
        }
    }

    /** Only for the holder of {@code running}. */
    private void rejectQueued() {
        do {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try {
                    rejected.accept(task);
                } catch (RuntimeException e) {
                    log.warn("Rejection handler failed for {}", task.getClass().getName(), e);
                }
            }
            running.set(false);
        } while (!tasks.isEmpty() && running.compareAndSet(false, true));
    }

    /**
     * Runs one batch, then hands the lane to a fresh start instead of holding the permit. An {@link Error} propagates
     * after the hand-on, so the tasks behind it keep their lane.
     */
    private void drain() {
        int ran = 0;
        Runnable task;
        while (!closed.getAsBoolean() && (ran < DRAIN_BATCH) && ((task = tasks.poll()) != null)) {
            ran++;
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Task {} failed", task.getClass().getName(), e);
            } catch (Error e) {
                handOn();
                throw e;
            }
        }
        if (closed.getAsBoolean()) {
            rejectQueued();
            return;
        }
        if (!tasks.isEmpty()) {
            handOn();
            return;
        }
        running.set(false);
        if (!tasks.isEmpty()) {
            schedule();
        }
    }

    /** Only for the holder of {@code running}. */
    private void handOn() {
        if (!start.start(drain, rejectQueued)) {
            rejectQueued();
        }
    }
}
