/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import io.netty.util.concurrent.EventExecutor;
import java.util.concurrent.RejectedExecutionException;

public final class EventLoopTasks {
    private EventLoopTasks() {}

    /** Runs inline on the event loop, otherwise hops to it; handler state stays confined to its loop. */
    public static void run(EventExecutor executor, Runnable action) {
        if (executor.inEventLoop()) {
            action.run();
        } else {
            try {
                executor.execute(action);
            } catch (RejectedExecutionException e) {
                // The loop is shutting down and its channels close with it, so the action has nothing left to guard.
                if (!executor.isShuttingDown()) {
                    throw e;
                }
            }
        }
    }
}
