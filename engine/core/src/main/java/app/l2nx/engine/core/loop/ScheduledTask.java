/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerEntry;
import app.l2nx.engine.core.loop.timer.TimerHandler;
import app.l2nx.engine.core.loop.timer.TimerQueue;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.ToString;

/**
 * {@link #cancel} from any thread returns {@code true} exactly when the task will not run again. A one-shot task about
 * to run is uncancellable; a periodic one stays cancellable while it runs, and a cancel stops the runs after it.
 */
@ToString(onlyExplicitlyIncluded = true)
final class ScheduledTask implements ScheduledFuture<Void>, TimerHandler {
    private static final int WAITING = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;
    private static final int CANCELLED = 3;
    private static final long NOT_ARMED = Long.MIN_VALUE;
    private static final VarHandle STATE;
    private static final VarHandle COMPLETION;

    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(ScheduledTask.class, "state", int.class);
            COMPLETION = lookup.findVarHandle(ScheduledTask.class, "completion", CountDownLatch.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Loop loop;

    @ToString.Include
    private final Runnable task;

    private final long delayMillis;

    @ToString.Include
    private final long periodMillis;

    private volatile int state;

    private volatile long nextRunAt = NOT_ARMED;

    private Throwable failure;

    private volatile CountDownLatch completion;

    private TimerEntry entry;

    ScheduledTask(Loop loop, Runnable task, long delayMillis, long periodMillis) {
        this.loop = loop;
        this.task = task;
        this.delayMillis = delayMillis;
        this.periodMillis = periodMillis;
    }

    void arm() {
        if (state != WAITING) {
            return;
        }
        entry = loop.timers(TimeBase.GAME)
                .schedule(
                        loop.gameNow() + delayMillis,
                        TimerSpec.builder().handler(this).period(periodMillis).build());
        nextRunAt = entry.getDeadline();
    }

    @Override
    public boolean isCurrent(Loop owner, TimerEntry timer) {
        return state == WAITING;
    }

    @Override
    public void onTimer(Loop owner, TimerEntry timer) {
        if (!STATE.compareAndSet(this, WAITING, RUNNING)) {
            return;
        }
        if (periodMillis == 0) {
            runOnce();
            return;
        }
        nextRunAt = TimerQueue.nextFixedRateDeadline(timer.getDeadline(), periodMillis, owner.gameNow());
        try {
            task.run();
        } finally {
            // Fails when cancelled during the run; the queue then drops the task as not current.
            STATE.compareAndSet(this, RUNNING, WAITING);
        }
    }

    private void runOnce() {
        entry = null;
        try {
            task.run();
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            state = DONE;
            releaseWaiters();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        int current;
        do {
            current = state;
            if (current != WAITING && (current != RUNNING || periodMillis == 0)) {
                return false;
            }
        } while (!STATE.compareAndSet(this, current, CANCELLED));
        releaseWaiters();
        if (loop.inLoop()) {
            removeFromQueue();
        } else {
            loop.offer(owner -> removeFromQueue());
        }
        return true;
    }

    void cancelOnStop() {
        if (STATE.compareAndSet(this, WAITING, CANCELLED)) {
            releaseWaiters();
        }
    }

    private void removeFromQueue() {
        if (entry != null) {
            loop.timers(TimeBase.GAME).cancel(entry);
            entry = null;
        }
    }

    @Override
    public boolean isCancelled() {
        return state == CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state >= DONE;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        final long runAt = nextRunAt;
        if (runAt == NOT_ARMED) {
            return unit.convert(delayMillis, TimeUnit.MILLISECONDS);
        }
        final long now = loop.inLoop() ? loop.gameNow() : loop.getClock().gameMillis();
        return unit.convert(runAt - now, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        if (!isDone()) {
            requireOffLoop();
            final CountDownLatch latch = completion();
            if (!isDone()) {
                latch.await();
            }
        }
        return result();
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!isDone()) {
            requireOffLoop();
            final CountDownLatch latch = completion();
            if (!isDone() && !latch.await(timeout, unit)) {
                throw new TimeoutException("Task " + task + " of loop " + loop.getName() + " is not done");
            }
        }
        return result();
    }

    private void requireOffLoop() {
        if (loop.inLoop()) {
            throw new IllegalStateException("Waiting on the thread of loop " + loop.getName() + " would never end");
        }
    }

    // Waiter: publish latch, then read state; completer: write state, then read latch, so no waiter is missed.
    private CountDownLatch completion() {
        final CountDownLatch latch = completion;
        if (latch != null) {
            return latch;
        }
        final CountDownLatch created = new CountDownLatch(1);
        final CountDownLatch raced = (CountDownLatch) COMPLETION.compareAndExchange(this, null, created);
        return raced == null ? created : raced;
    }

    private void releaseWaiters() {
        final CountDownLatch latch = completion;
        if (latch != null) {
            latch.countDown();
        }
    }

    private Void result() throws ExecutionException {
        if (state == CANCELLED) {
            throw new CancellationException("Task " + task + " of loop " + loop.getName() + " was cancelled");
        }
        if (failure != null) {
            throw new ExecutionException(failure);
        }
        return null;
    }
}
