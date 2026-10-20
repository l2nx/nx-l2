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

    private NetworkServer(
            String name, Channel serverChannel, ChannelGroup clients, ScheduledFuture<?> sweep, Duration timeout) {
        this.name = name;
        this.serverChannel = serverChannel;
        this.clients = clients;
        this.sweep = sweep;
        shutdownTimeout = timeout;
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
                        clients.add(channel);
                        initializer.accept(channel);
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
        return new NetworkServer(name, bound.channel(), clients, sweep, properties.shutdownTimeout());
    }

    static WriteBufferWaterMark watermark(NetworkProperties properties) {
        return new WriteBufferWaterMark((int) properties.writeBufferLow().toBytes(), (int)
                properties.writeBufferHigh().toBytes());
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) serverChannel.localAddress();
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
        stopAccepting();
        if (!clients.close().awaitUninterruptibly(shutdownTimeout.toMillis())) {
            log.warn("{}: clients did not close within {}", name, shutdownTimeout);
        }
    }
}
