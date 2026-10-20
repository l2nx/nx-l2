/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import app.l2nx.engine.core.loop.Message;
import java.lang.invoke.VarHandle;

/**
 * Loop side of a client session queue. Producer: enqueue, {@link VarHandle#fullFence()}, then {@code if
 * (source.trySignal()) loop.signal(source)}. Loop: when drained, {@link #clearSignal()}, full fence, re-check
 * {@link #hasInput()}. The paired fences keep an enqueue racing a clear from being lost; the producer's fence is
 * required because a JCTools offer is only a release store, and queue and flag accesses must be atomic or stronger.
 */
public interface InputSource {
    /** Stable id for the replay record. */
    long id();

    /**
     * Owner thread: removes and returns the next item, or {@code null} when there is none. The item leaves the queue
     * here, before it runs, so an item that fails is not taken again.
     */
    Message poll();

    boolean hasInput();

    /** {@code true} only on the transition from idle to signalled. */
    boolean trySignal();

    void clearSignal();
}
