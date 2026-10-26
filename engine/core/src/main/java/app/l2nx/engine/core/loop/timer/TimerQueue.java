/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.FailureHandler;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.PassPhase;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import lombok.Getter;

/**
 * Pending timers of one time base in {@code (deadline, sequence)} order on a JDK binary heap. Cancellation is lazy: a
 * cancelled entry stays until it reaches the head or cancelled entries make up half the heap. The queue keeps no
 * clock of its own, so a wall clock set back only means a smaller {@code now}. Owner thread only.
 */
public final class TimerQueue {
    private final PriorityQueue<TimerEntry> heap = new PriorityQueue<>();
    private final FailureHandler failures;
    private long nextSequence;
    private int cancelledPending;
    private boolean running;
    private long runNow;
    private TimerEntry firing;

    @Getter
    private int lastOverdue;

    public TimerQueue(FailureHandler failures) {
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /**
     * The deadline after {@code now} on the period grid of {@code deadline}: runs missed by a late pass are skipped, not
     * replayed in a burst, and the phase is kept.
     */
    public static long nextFixedRateDeadline(long deadline, long period, long now) {
        return deadline + period * (Math.max(0, now - deadline) / period + 1);
    }

    /**
     * While {@link #runDue} fires, the deadline is floored to {@code now + 1}, so a handler re-arming at a due deadline
     * cannot loop in one pass or starve the entries behind it. The returned entry carries the floored deadline.
     */
    public TimerEntry schedule(long deadline, TimerSpec spec) {
        final long armed = running ? Math.max(deadline, runNow + 1) : deadline;
        final TimerEntry entry = new TimerEntry(this, Objects.requireNonNull(spec, "spec"), nextSequence++, armed);
        entry.queued = true;
        heap.add(entry);
        return entry;
    }

    /**
     * {@code true} exactly when this call stops the entry from firing again: it was pending, or it is a fixed-rate entry
     * whose handler is running now.
     *
     * @throws IllegalArgumentException when the entry was scheduled on another queue
     */
    public boolean cancel(TimerEntry entry) {
        if (entry.queue != this) {
            throw new IllegalArgumentException("Timer entry " + entry + " belongs to another queue");
        }
        if (entry.cancelled) {
            return false;
        }
        if (entry.queued) {
            markCancelled(entry);
            purgeIfMostlyCancelled();
            return true;
        }
        if (entry == firing && entry.isPeriodic()) {
            entry.cancelled = true;
            return true;
        }
        return false;
    }

    /**
     * Cancels at once the pending entries of {@code handler} that are no longer current, so a mass generation bump
     * leaves nothing in {@link #size} or {@link #nextDeadline}. O(n): for rare bumps.
     *
     * @return the number of entries cancelled
     */
    public int cancelStale(Loop loop, TimerHandler handler) {
        int cancelled = 0;
        for (TimerEntry entry : heap) {
            if (!entry.cancelled && entry.getSpec().handler() == handler && !isCurrent(loop, entry)) {
                markCancelled(entry);
                cancelled++;
            }
        }
        purgeIfMostlyCancelled();
        return cancelled;
    }

    public int size() {
        return heap.size() - cancelledPending;
    }

    /** Every pending entry, in no particular order. */
    public void forEach(Consumer<TimerEntry> action) {
        for (TimerEntry entry : heap) {
            if (!entry.cancelled) {
                action.accept(entry);
            }
        }
    }

    /** The earliest pending deadline; {@link Long#MAX_VALUE} when nothing is pending. */
    public long nextDeadline() {
        TimerEntry head;
        while ((head = heap.peek()) != null && head.cancelled) {
            heap.poll();
            cancelledPending--;
        }
        return head == null ? Long.MAX_VALUE : head.deadline;
    }

    /**
     * Fires the entries due by {@code now} in {@code (deadline, sequence)} order. At most {@code overdueCap} entries with
     * a deadline before {@code lateBefore} fire per run; the rest keeps its place for the next run.
     */
    public int runDue(Loop loop, long now, int overdueCap, long lateBefore) {
        running = true;
        runNow = now;
        int fired = 0;
        int overdue = 0;
        try {
            TimerEntry entry;
            while ((entry = heap.peek()) != null && entry.deadline <= now) {
                if (entry.cancelled) {
                    heap.poll();
                    cancelledPending--;
                    continue;
                }
                // Before the cap check: entries made stale by a mass generation bump must not use the cap up.
                if (!isCurrent(loop, entry)) {
                    heap.poll();
                    entry.queued = false;
                    continue;
                }
                final boolean late = entry.deadline < lateBefore;
                if (late && overdue >= overdueCap) {
                    break;
                }
                heap.poll();
                entry.queued = false;
                if (late) {
                    overdue++;
                }
                fire(loop, entry);
                fired++;
                if (entry.isPeriodic() && !entry.cancelled && isCurrent(loop, entry)) {
                    entry.deadline = nextFixedRateDeadline(
                            entry.deadline, entry.getSpec().period(), now);
                    entry.queued = true;
                    heap.add(entry);
                }
            }
        } finally {
            running = false;
        }
        lastOverdue = overdue;
        return fired;
    }

    int heapSize() {
        return heap.size();
    }

    private void markCancelled(TimerEntry entry) {
        entry.cancelled = true;
        cancelledPending++;
    }

    // O(n) once per n/2 cancels: a timer re-armed on every event costs O(1) amortized and the heap stays bounded.
    private void purgeIfMostlyCancelled() {
        if (cancelledPending * 2 > heap.size()) {
            heap.removeIf(TimerEntry::isCancelled);
            cancelledPending = 0;
        }
    }

    private boolean isCurrent(Loop loop, TimerEntry entry) {
        try {
            return entry.getSpec().handler().isCurrent(loop, entry);
        } catch (RuntimeException e) {
            failures.onFailure(PassPhase.DEADLINES, entry, e);
            return false;
        }
    }

    private void fire(Loop loop, TimerEntry entry) {
        firing = entry;
        try {
            entry.getSpec().handler().onTimer(loop, entry);
        } catch (RuntimeException e) {
            failures.onFailure(PassPhase.DEADLINES, entry, e);
        } finally {
            firing = null;
        }
    }
}
