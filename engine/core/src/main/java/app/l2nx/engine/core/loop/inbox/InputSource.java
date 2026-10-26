/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import app.l2nx.engine.core.loop.Message;
import java.lang.invoke.VarHandle;

/**
 * Producer: enqueue, {@link VarHandle#fullFence()}, then {@code if (source.trySignal()) loop.signal(source)}. Loop: when
 * drained, {@link #clearSignal()}, full fence, re-check {@link #hasInput()}. The paired fences keep an enqueue racing a
 * clear from being lost; the producer's is needed because a JCTools offer is only a release store.
 */
public interface InputSource {
    /** Owner thread: the next item or {@code null}. It leaves the queue before it runs, so a failing item is not retried. */
    Message poll();

    boolean hasInput();

    /** {@code true} only on the transition from idle to signalled. */
    boolean trySignal();

    void clearSignal();
}
