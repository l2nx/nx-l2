/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.transport;

import app.l2nx.network.config.NetworkProperties;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * One boss thread and one worker group per process; every bootstrap of the process shares them.
 */
@Slf4j
public final class NetworkEventLoops implements AutoCloseable {
    private final ResolvedTransport transport;
    private final EventLoopGroup boss;
    private final EventLoopGroup workers;
    private final Duration quietPeriod;
    private final Duration timeout;

    public NetworkEventLoops(NetworkProperties properties, TransportResolver resolver) {
        this(properties, resolver.resolve(properties.transport()));
        log.info("Network transport {}", transport.transport());
    }

    private NetworkEventLoops(NetworkProperties properties, ResolvedTransport transport) {
        this(
                properties,
                transport,
                new MultiThreadIoEventLoopGroup(
                        1, new DefaultThreadFactory("l2nx-net-boss"), transport.ioHandlerFactory()));
    }

    private NetworkEventLoops(NetworkProperties properties, ResolvedTransport transport, EventLoopGroup boss) {
        this(
                transport,
                boss,
                orShutDown(
                        boss,
                        () -> new MultiThreadIoEventLoopGroup(
                                properties.workerThreads(),
                                new DefaultThreadFactory("l2nx-net-io"),
                                transport.ioHandlerFactory())),
                properties);
    }

    static EventLoopGroup orShutDown(EventLoopGroup boss, Supplier<EventLoopGroup> workers) {
        try {
            return workers.get();
        } catch (RuntimeException | Error e) {
            boss.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            throw e;
        }
    }

    NetworkEventLoops(
            ResolvedTransport transport, EventLoopGroup boss, EventLoopGroup workers, NetworkProperties properties) {
        this.transport = transport;
        this.boss = boss;
        this.workers = workers;
        quietPeriod = properties.shutdownQuietPeriod();
        timeout = properties.shutdownTimeout();
    }

    public ResolvedTransport transport() {
        return transport;
    }

    public EventLoopGroup boss() {
        return boss;
    }

    public EventLoopGroup workers() {
        return workers;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        Future<?> bossDone = null;
        Future<?> workersDone = null;
        try {
            bossDone = shutdownGracefully(boss);
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            workersDone = shutdownGracefully(workers);
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        // The quiet period may run up to the timeout, so the wait allows both plus a margin for the last tasks.
        final long waitMillis = quietPeriod.toMillis() + timeout.toMillis() + 1_000;
        if (bossDone != null) {
            bossDone.awaitUninterruptibly(waitMillis);
        }
        if (workersDone != null) {
            workersDone.awaitUninterruptibly(waitMillis);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private Future<?> shutdownGracefully(EventLoopGroup group) {
        return group.shutdownGracefully(quietPeriod.toMillis(), timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
