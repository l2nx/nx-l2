/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.network.channel.CloseReason;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeTransport implements SessionTransport {
    final List<String> changes = new CopyOnWriteArrayList<>();
    final List<CloseReason> closes = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
    }

    @Override
    public void stateChanged(SessionState from, SessionState to) {
        changes.add(from + "->" + to);
    }

    @Override
    public void close(CloseReason reason) {
        closes.add(reason);
    }

    public void runTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }
}
