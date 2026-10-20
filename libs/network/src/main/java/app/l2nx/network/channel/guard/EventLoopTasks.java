/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import io.netty.util.concurrent.EventExecutor;

final class EventLoopTasks {
    private EventLoopTasks() {}

    /** Runs inline on the event loop, otherwise hops to it; handler state stays confined to its loop. */
    static void run(EventExecutor executor, Runnable action) {
        if (executor.inEventLoop()) {
            action.run();
        } else {
            executor.execute(action);
        }
    }
}
