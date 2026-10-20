/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue;

/** Many producers, one consumer: the loop thread that owns the inbox. */
public final class Inbox<M> {
    private static final int CHUNK_SIZE = 1024;

    private final MpscUnboundedAtomicArrayQueue<M> queue = new MpscUnboundedAtomicArrayQueue<>(CHUNK_SIZE);
    // Offers in flight; termination waits for them so an accepted message is never left behind the last drain.
    private final AtomicInteger offering = new AtomicInteger();
    private volatile boolean terminated;
    private volatile Wakeup wakeup = Wakeup.NONE;

    public boolean offer(M message) {
        Objects.requireNonNull(message, "message");
        offering.incrementAndGet();
        try {
            if (terminated) {
                return false;
            }
            queue.offer(message);
        } finally {
            offering.decrementAndGet();
        }
        wakeup.wake();
        return true;
    }

    public M poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void wakeup(Wakeup wakeup) {
        this.wakeup = Objects.requireNonNull(wakeup, "wakeup");
    }

    public void terminate() {
        terminated = true;
        while (offering.get() != 0) {
            Thread.onSpinWait();
        }
    }
}
