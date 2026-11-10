/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import io.netty.util.concurrent.EventExecutor;

/**
 * Records an event loop in a hand-off as a deferring {@link OutboundQueue} does, for tests that may not load the queue:
 * its jctools queue reaches for {@code sun.misc.Unsafe}, which the engine's tests deny.
 */
public final class TestHandoffs {
    private TestHandoffs() {}

    public static void defer(OutboundHandoff handoff, EventExecutor executor) {
        handoff.defer(executor);
    }
}
