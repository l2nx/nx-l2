/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import app.l2nx.network.channel.guard.ConnectionLimiter;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NetworkServer implements AutoCloseable {
    private static final long SWEEP_MINUTES = 1;

    private final String name;
    private final Channel serverChannel;
    private final ChannelGroup clients;
    private final ScheduledFuture<?> sweep;
    private final Duration shutdownTimeout;
    private final AtomicBoolean closing;

    private NetworkServer(
            String name,
            Channel serverChannel,
            ChannelGroup clients,
            ScheduledFuture<?> sweep,
            Duration timeout,
            AtomicBoolean closing) {
        this.name = name;
        this.serverChannel = serverChannel;
        this.clients = clients;
        this.sweep = sweep;
        shutdownTimeout = timeout;
        this.closing = closing;
    }

    public static NetworkServer bind(
            String name,
            InetSocketAddress address,
            NetworkEventLoops loops,
            NetworkProperties properties,
            NetworkMetrics metrics,
            ConnectionLimiter limiter,
            Consumer<Channel> initializer) {
        final ChannelGroup clients = new DefaultChannelGroup(name, GlobalEventExecutor.INSTANCE);
        final AtomicBoolean closing = new AtomicBoolean();
        final ServerBootstrap bootstrap = new ServerBootstrap()
                .group(loops.boss(), loops.workers())
                .channel(loops.transport().serverChannelClass())
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, watermark(properties))
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        channel.attr(NetworkMetrics.KEY).set(metrics);
                        metrics.connectionOpened();
                        channel.closeFuture().addListener(future -> metrics.connectionClosed());
                        if ((limiter != null) && !limiter.tryAccept(channel)) {
                            channel.close();
                            return;
                        }
                        initializer.accept(channel);
                        // After the pipeline is built: a shutdown farewell relies on it.
                        clients.add(channel);
                        // Accepted before the close but initialized after it: the close no longer sees this channel.
                        if (closing.get()) {
                            channel.close();
                        }
                    }
                });

        final ChannelFuture bound = bootstrap.bind(address).awaitUninterruptibly();
        if (!bound.isSuccess()) {
            throw new IllegalStateException(name + " could not listen on " + address, bound.cause());
        }

        final ScheduledFuture<?> sweep = limiter == null
                ? null
                : loops.boss().scheduleAtFixedRate(limiter::sweep, SWEEP_MINUTES, SWEEP_MINUTES, TimeUnit.MINUTES);
        log.info("{} listening on {}", name, bound.channel().localAddress());
        return new NetworkServer(name, bound.channel(), clients, sweep, properties.shutdownTimeout(), closing);
    }

    static WriteBufferWaterMark watermark(NetworkProperties properties) {
        return new WriteBufferWaterMark((int) properties.writeBufferLow().toBytes(), (int)
                properties.writeBufferHigh().toBytes());
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) serverChannel.localAddress();
    }

    public boolean isListening() {
        return serverChannel.isActive();
    }

    public ChannelGroup clients() {
        return clients;
    }

    public void stopAccepting() {
        if (sweep != null) {
            sweep.cancel(false);
        }
        serverChannel.close().awaitUninterruptibly(shutdownTimeout.toMillis());
    }

    public void stopReading() {
        clients.forEach(channel -> channel.config().setAutoRead(false));
    }

    @Override
    public void close() {
        close(Channel::close);
    }

    /**
     * {@code farewell} sends the last frame with a close-on-write promise, or closes the channel itself; clients still
     * open after {@code shutdown-timeout} are closed.
     */
    public void close(Consumer<Channel> farewell) {
        stopAccepting();
        closing.set(true);
        clients.forEach(farewell);
        if (clients.newCloseFuture().awaitUninterruptibly(shutdownTimeout.toMillis())) {
            return;
        }
        log.warn("{}: clients did not take their last frame within {}", name, shutdownTimeout);
        clients.close().awaitUninterruptibly(shutdownTimeout.toMillis());
    }
}
