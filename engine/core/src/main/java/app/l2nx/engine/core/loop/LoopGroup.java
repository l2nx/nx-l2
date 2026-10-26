/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** A loop stays on its thread for its lifetime. */
@Slf4j
public final class LoopGroup {
    private final String prefix;
    private final MeterRegistry registry;
    private final ThreadFailureHandler failures;
    private final LoopWatchdog watchdog;
    private final List<LoopThread> threads = new ArrayList<>();
    private boolean stopped;

    public LoopGroup(String prefix, MeterRegistry registry, EngineProperties settings, ThreadFailureHandler failures) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.failures = Objects.requireNonNull(failures, "failures");
        // The loops time their passes with the registry's clock, so the watchdog reads the same one.
        this.watchdog = new LoopWatchdog(
                Objects.requireNonNull(settings, "settings"), registry.config().clock()::monotonicTime, failures);
    }

    /** Pins the loop to a thread of its own named {@code <prefix>-<loop name>}. */
    public synchronized void pin(Loop loop) {
        if (stopped) {
            throw new IllegalStateException("Loop group " + prefix + " is shut down");
        }
        final LoopThread thread = new LoopThread(prefix + "-" + loop.getName(), loop, registry, failures);
        thread.start();
        threads.add(thread);
        watchdog.watch(loop, thread.getThread());
        watchdog.start(prefix + "-watchdog");
    }

    public void disarmExit() {
        watchdog.disarmExit();
    }

    /**
     * Waits up to {@code timeout} for every loop to drain and run its shutdown hooks; returns the names of threads
     * still running then. Later calls return at once.
     */
    public List<String> shutdown(Duration timeout) {
        final List<LoopThread> stopping;
        synchronized (this) {
            if (stopped) {
                return List.of();
            }
            stopped = true;
            stopping = List.copyOf(threads);
        }
        disarmExit();
        stopping.forEach(LoopThread::requestStop);
        final Clock clock = registry.config().clock();
        final long deadline = clock.monotonicTime() + timeout.toNanos();
        final List<String> alive = new ArrayList<>();
        boolean interrupted = false;
        for (LoopThread thread : stopping) {
            while (true) {
                try {
                    if (!thread.join(Duration.ofNanos(deadline - clock.monotonicTime()))) {
                        alive.add(thread.getName());
                    }
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            thread.close();
        }
        // Closed last so a drain pass blocked during the stop still gets its stack logged.
        watchdog.close();
        if (!alive.isEmpty()) {
            // A shutdown hook that never returns must not hold the process; the rest of the stop goes on without it.
            log.warn(
                    "Loop group {} stopped waiting after {} ms; loop threads still running: {}",
                    prefix,
                    timeout.toMillis(),
                    alive);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return alive;
    }
}
