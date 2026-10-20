/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads;

import app.l2nx.highfive.threads.metrics.LegacyTaskMetrics;
import app.l2nx.highfive.threads.metrics.ThreadPoolKind;
import java.lang.Thread.UncaughtExceptionHandler;

final class RunnableWrapper implements Runnable {
    private final Runnable task;
    private final LegacyTaskMetrics.TaskTimer timer;

    RunnableWrapper(LegacyTaskMetrics metrics, ThreadPoolKind pool, Runnable task) {
        this.task = task;
        this.timer = metrics.timer(pool, task.getClass());
    }

    @Override
    public void run() {
        try {
            timer.run(task);
        } catch (Throwable t) {
            final Thread currentThread = Thread.currentThread();
            final UncaughtExceptionHandler exceptionHandler = currentThread.getUncaughtExceptionHandler();
            if (exceptionHandler != null) {
                exceptionHandler.uncaughtException(currentThread, t);
            }
        }
    }

    @Override
    public String toString() {
        return timer.getNamespace();
    }
}
