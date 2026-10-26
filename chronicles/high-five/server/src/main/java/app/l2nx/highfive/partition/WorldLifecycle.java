/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.WorldLoader;
import app.l2nx.highfive.WorldShutdown;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Loads the world in the loop's startup pass, so a load failure fails the start before the game port opens. The
 * shutdown work is also a loop shutdown hook, since a dead loop thread stops the loop before this stop; it runs once.
 */
@Slf4j
@RequiredArgsConstructor
@Component
class WorldLifecycle implements SmartLifecycle {
    static final int PHASE = PartitionRuntime.PHASE + 256;

    private final PartitionRuntime runtime;
    private final WorldLoader loader;
    private final WorldShutdown shutdown;
    private final OnlinePlayersGauge onlinePlayers;
    private final List<WorldSystem> systems;
    private volatile boolean running;
    private boolean worldShutDown;

    @Override
    public void start() {
        final Loop world = runtime.world();
        WorldPartition.bind(world);
        final CompletableFuture<Void> loaded = new CompletableFuture<>();
        if (!world.offerStartup(loop -> runInto(loaded, () -> load(loop)))) {
            throw new IllegalStateException("The world loop stopped before the world was loaded");
        }
        await(loaded);
        running = true;
    }

    @Override
    public void stop() {
        try {
            final CompletableFuture<Void> done = new CompletableFuture<>();
            try {
                WorldPartition.offer(() -> runInto(done, this::shutDownWorld));
            } catch (RejectedExecutionException e) {
                log.warn("The world loop stopped before the server; the world shutdown ran in its shutdown hooks");
                return;
            }
            await(done);
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    private void load(Loop loop) {
        onlinePlayers.install(loop);
        systems.forEach(system -> system.install(loop));
        loader.load();
        loop.addShutdownHook(_ -> shutDownWorld());
    }

    private void shutDownWorld() {
        if (!worldShutDown) {
            worldShutDown = true;
            shutdown.run();
        }
    }

    // An Error is handed over too: thrown on the loop it would kill the loop thread instead of failing the caller.
    private static void runInto(CompletableFuture<Void> done, Runnable work) {
        try {
            work.run();
            done.complete(null);
        } catch (RuntimeException | Error e) {
            done.completeExceptionally(e);
        }
    }

    private static void await(CompletableFuture<Void> done) {
        try {
            done.join();
        } catch (CompletionException e) {
            switch (e.getCause()) {
                case RuntimeException failure -> throw failure;
                case Error failure -> throw failure;
                case null, default -> throw e;
            }
        }
    }
}
