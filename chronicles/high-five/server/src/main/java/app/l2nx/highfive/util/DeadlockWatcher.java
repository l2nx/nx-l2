/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.util;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * A dedicated thread to periodically monitor for deadlocks and provide detailed reports when detected.<br>
 * Allows for custom action when a deadlock is found by executing a user-defined callback.
 * @author Mobius
 */
@Slf4j
public class DeadlockWatcher extends Thread {

    private static final int MAX_STACK_DEPTH = 30; // Max stack trace elements to log.
    private static final int MAX_DEADLOCK_THREADS = 20; // Max threads to process in full report.

    private final Duration checkInterval;
    private final Runnable deadlockCallback;
    private final ThreadMXBean threadMXBean;

    public DeadlockWatcher(Duration checkInterval, Runnable deadlockCallback) {
        super("DeadlockWatcher");
        this.checkInterval = checkInterval;
        this.deadlockCallback = deadlockCallback;
        threadMXBean = ManagementFactory.getThreadMXBean();
    }

    @Override
    public void run() {
        log.info("Thread started.");

        while (!isInterrupted()) {
            try {
                // Detect deadlocks and handle them if found.
                final long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
                if (deadlockedThreadIds != null) {
                    log.warn("Deadlock detected!");

                    // Build detailed deadlock report.
                    if (deadlockedThreadIds.length > MAX_DEADLOCK_THREADS) {
                        generateMinimalDeadlockReport(deadlockedThreadIds);
                    } else {
                        generateDeadlockReport(deadlockedThreadIds);
                    }

                    // Invoke callback if set.
                    if (deadlockCallback != null) {
                        try {
                            deadlockCallback.run();
                        } catch (Exception e) {
                            log.error("Exception in deadlock callback: ", e);
                        }
                    }
                }

                Thread.sleep(checkInterval.toMillis());
            } catch (InterruptedException e) {
                log.info("Thread interrupted and will exit.");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Exception during deadlock check: ", e);
            }
        }

        log.info("Thread terminated.");
    }

    private void generateDeadlockReport(long[] deadlockedThreadIds) {
        log.warn("========== DEADLOCK REPORT ==========");

        for (ThreadInfo info : threadMXBean.getThreadInfo(deadlockedThreadIds, true, true)) {
            if (info == null) {
                continue;
            }

            log.warn("Thread: {} (ID: {})", info.getThreadName(), info.getThreadId());
            log.warn("State: {}", info.getThreadState());
            final String lockName = info.getLockName();
            if (lockName != null) {
                log.warn("Waiting for lock: {}", lockName);
                log.warn("Lock owner: {} (ID: {})", info.getLockOwnerName(), info.getLockOwnerId());
            }

            log.warn("Stack trace:");
            final StackTraceElement[] stack = info.getStackTrace();
            for (int i = 0; i < Math.min(stack.length, MAX_STACK_DEPTH); i++) {
                log.warn("\tat {}", stack[i]);
            }

            if (stack.length > MAX_STACK_DEPTH) {
                log.warn("\t... (stack trace truncated)");
            }

            final MonitorInfo[] lockedMonitors = info.getLockedMonitors();
            if (lockedMonitors.length > 0) {
                log.warn("Locked monitors:");
                for (MonitorInfo monitor : lockedMonitors) {
                    log.warn(
                            "\t- {} at line {}",
                            monitor.getClassName(),
                            monitor.getLockedStackFrame().getLineNumber());
                }
            }

            final LockInfo[] lockedSynchronizers = info.getLockedSynchronizers();
            if (lockedSynchronizers.length > 0) {
                log.warn("Locked synchronizers:");
                for (LockInfo lock : lockedSynchronizers) {
                    log.warn("\t- {}", lock.getClassName());
                }
            }
        }

        log.warn("========== END DEADLOCK REPORT ==========");
    }

    private void generateMinimalDeadlockReport(long[] deadlockedThreadIds) {
        log.warn("========== MINIMAL DEADLOCK REPORT ==========");

        for (long id : deadlockedThreadIds) {
            final ThreadInfo info = threadMXBean.getThreadInfo(id, 10);
            if (info != null) {
                log.warn("Thread: {} (State: {})", info.getThreadName(), info.getThreadState());
                final String lockName = info.getLockName();
                if (lockName != null) {
                    log.warn("\tWaiting for: {}", lockName);
                }

                final StackTraceElement[] stack = info.getStackTrace();
                final int frames = Math.min(5, stack.length);
                for (int i = 0; i < frames; i++) {
                    log.warn("\tat {}", stack[i]);
                }
            }
        }

        log.warn("========== END MINIMAL REPORT ==========");
    }
}
