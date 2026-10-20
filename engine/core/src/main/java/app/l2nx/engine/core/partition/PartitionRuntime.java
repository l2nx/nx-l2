/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.partition;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopGroup;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * Stop waits at most {@code stopTimeout} for the world loop to drain; a dying loop thread exits with
 * {@link ServerExitCode#CRASH} so the supervisor restarts the process.
 */
@Slf4j
@RequiredArgsConstructor
public class PartitionRuntime implements SmartLifecycle {
    /**
     * Starts after Boot's web server and graceful shutdown lifecycles and before the game port ({@code DEFAULT_PHASE});
     * stops after the game port closes and before the web server.
     */
    public static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 512;

    public static final String WORLD = "world";

    static final String THREAD_PREFIX = "l2nx-loop";

    private final EngineProperties properties;
    private final GameClock clock;
    private final MeterRegistry registry;
    private final LongSupplier seeds;
    private final Function<String, PassRecorder> recorders;
    private final ServerExit serverExit;
    private final Duration stopTimeout;
    private volatile LoopGroup group;
    private volatile Loop world;

    @Override
    public synchronized void start() {
        if (group != null) {
            return;
        }
        final LoopGroup started = new LoopGroup(THREAD_PREFIX, registry, this::onThreadFailure);
        try {
            final Loop created =
                    new Loop(WORLD, clock, seeds.getAsLong(), properties, registry, recorders.apply(WORLD));
            started.pin(created);
            world = created;
            group = started;
        } catch (RuntimeException e) {
            started.shutdown(stopTimeout);
            throw e;
        }
        log.info("Partition runtime started: the world loop runs on its own thread");
    }

    // Asynchronous so spring.lifecycle.timeout-per-shutdown-phase can bound a shutdown hook that never returns.
    @Override
    public void stop(Runnable callback) {
        final LoopGroup stopping = detach();
        if (stopping == null) {
            callback.run();
            return;
        }
        Thread.ofPlatform().name(THREAD_PREFIX + "-stop").start(() -> {
            try {
                shutdown(stopping);
            } finally {
                callback.run();
            }
        });
    }

    @Override
    public void stop() {
        final LoopGroup stopping = detach();
        if (stopping != null) {
            shutdown(stopping);
        }
    }

    @Override
    public boolean isRunning() {
        return group != null;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    public Loop world() {
        final Loop current = world;
        if (current == null) {
            throw new IllegalStateException("The partition runtime is not running");
        }
        return current;
    }

    private synchronized LoopGroup detach() {
        final LoopGroup stopping = group;
        group = null;
        world = null;
        return stopping;
    }

    private void shutdown(LoopGroup stopping) {
        if (stopping.shutdown(stopTimeout).isEmpty()) {
            log.info("Partition runtime stopped");
        }
    }

    // The loop thread has already logged the failure; the world cannot go on without it.
    private void onThreadFailure(Thread thread, Throwable failure) {
        log.error("Loop thread {} died, exiting with {}", thread.getName(), ServerExitCode.CRASH);
        serverExit.exit(ServerExitCode.CRASH);
    }
}
