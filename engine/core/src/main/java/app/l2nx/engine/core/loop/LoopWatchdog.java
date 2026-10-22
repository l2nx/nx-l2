/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports a pass blocked past {@code blocked-pass-warning} or {@code blocked-pass-exit}; {@code pass-budget} is checked
 * only when a pass ends, so a pass that never ends would go unreported.
 */
@Slf4j
final class LoopWatchdog {
    static final Duration CHECK_INTERVAL = Duration.ofMillis(500);

    private final long warningNanos;
    private final long exitNanos;
    private final LongSupplier nanoTime;
    private final ThreadFailureHandler failures;
    private final List<Watched> watched = new CopyOnWriteArrayList<>();
    private Thread thread;
    private volatile boolean closed;
    private volatile boolean exitDisarmed;
    private int warnings;

    LoopWatchdog(EngineProperties settings, LongSupplier nanoTime, ThreadFailureHandler failures) {
        this.warningNanos = settings.blockedPassWarning().toNanos();
        this.exitNanos = settings.blockedPassExit() == null
                ? Long.MAX_VALUE
                : settings.blockedPassExit().toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    void watch(Loop loop, Thread loopThread) {
        watched.add(new Watched(Objects.requireNonNull(loop, "loop"), Objects.requireNonNull(loopThread, "thread")));
    }

    synchronized void start(String name) {
        if (thread == null && !closed) {
            thread = Thread.ofPlatform().name(name).daemon(true).start(this::run);
        }
    }

    void disarmExit() {
        exitDisarmed = true;
    }

    void close() {
        final Thread running;
        synchronized (this) {
            closed = true;
            running = thread;
        }
        if (running == null) {
            return;
        }
        LockSupport.unpark(running);
        if (running == Thread.currentThread()) {
            return;
        }
        boolean interrupted = false;
        while (true) {
            try {
                running.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    void check() {
        final long now = nanoTime.getAsLong();
        for (Watched entry : watched) {
            check(entry, now);
        }
    }

    int warnings() {
        return warnings;
    }

    private void run() {
        final long intervalNanos = CHECK_INTERVAL.toNanos();
        while (!closed) {
            LockSupport.parkNanos(this, intervalNanos);
            if (closed) {
                return;
            }
            try {
                check();
            } catch (RuntimeException e) {
                // A dead watchdog would leave every later blocked pass unreported.
                log.error("Loop watchdog check failed", e);
            }
        }
    }

    private void check(Watched entry, long now) {
        final long start = entry.loop.runningPassStartNanos();
        if (start == Loop.IDLE) {
            return;
        }
        if (start != entry.passStart) {
            entry.passStart = start;
            entry.warned = 0;
        }
        final long blockedNanos = now - start;
        if (blockedNanos > exitNanos && !exitDisarmed) {
            if (!entry.exitRequested) {
                entry.exitRequested = true;
                exit(entry, blockedNanos);
            }
            return;
        }
        final long thresholds = blockedNanos / warningNanos;
        if (thresholds > entry.warned) {
            entry.warned = thresholds;
            warnings++;
            final String loop = entry.loop.getName();
            log.atWarn()
                    .addKeyValue("loop", loop)
                    .setCause(blocked(entry, blockedNanos))
                    .log(
                            "Pass of loop {} has been blocked for {} ms on thread {}, warning limit {} ms",
                            loop,
                            TimeUnit.NANOSECONDS.toMillis(blockedNanos),
                            entry.thread.getName(),
                            TimeUnit.NANOSECONDS.toMillis(warningNanos));
        }
    }

    private void exit(Watched entry, long blockedNanos) {
        final String loop = entry.loop.getName();
        final IllegalStateException failure = blocked(entry, blockedNanos);
        log.atError()
                .addKeyValue("loop", loop)
                .setCause(failure)
                .log(
                        "Pass of loop {} has been blocked for {} ms on thread {}, exit limit {} ms; exiting",
                        loop,
                        TimeUnit.NANOSECONDS.toMillis(blockedNanos),
                        entry.thread.getName(),
                        TimeUnit.NANOSECONDS.toMillis(exitNanos));
        failures.onThreadFailure(entry.thread, failure);
    }

    private static IllegalStateException blocked(Watched entry, long blockedNanos) {
        final IllegalStateException blocked = new IllegalStateException("Pass of loop " + entry.loop.getName()
                + " blocked for " + TimeUnit.NANOSECONDS.toMillis(blockedNanos) + " ms");
        blocked.setStackTrace(entry.thread.getStackTrace());
        return blocked;
    }

    private static final class Watched {
        private final Loop loop;
        private final Thread thread;
        private long passStart = Loop.IDLE;
        private long warned;
        private boolean exitRequested;

        private Watched(Loop loop, Thread thread) {
            this.loop = loop;
            this.thread = thread;
        }
    }
}
