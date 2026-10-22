/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.network.transport.ResolvedTransport;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.function.Consumer;

public final class NetworkClient {
    private NetworkClient() {}

    public static ChannelFuture connect(
            InetSocketAddress address,
            NetworkEventLoops loops,
            NetworkProperties properties,
            NetworkMetrics metrics,
            Consumer<Channel> initializer) {
        return bootstrap(loops.workers(), loops.transport(), properties, metrics, initializer)
                .connect(address);
    }

    /**
     * Connects on a group the caller owns, such as one event loop that also runs the connection's logic.
     *
     * @param transport the transport {@code group} was created with; the channel class must match its I/O handler
     */
    public static ChannelFuture connect(
            InetSocketAddress address,
            EventLoopGroup group,
            ResolvedTransport transport,
            Duration connectTimeout,
            NetworkProperties properties,
            NetworkMetrics metrics,
            Consumer<Channel> initializer) {
        if (connectTimeout == null || !connectTimeout.isPositive()) {
            throw new IllegalArgumentException("The connect timeout must be positive: " + connectTimeout);
        }
        // Netty takes whole milliseconds as an int: a sub-millisecond timeout still waits one.
        final int connectTimeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1, connectTimeout.toMillis()));
        return bootstrap(group, transport, properties, metrics, initializer)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .connect(address);
    }

    private static Bootstrap bootstrap(
            EventLoopGroup group,
            ResolvedTransport transport,
            NetworkProperties properties,
            NetworkMetrics metrics,
            Consumer<Channel> initializer) {
        return new Bootstrap()
                .group(group)
                .channel(transport.socketChannelClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, NetworkServer.watermark(properties))
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        channel.attr(NetworkMetrics.KEY).set(metrics);
                        metrics.connectionOpened();
                        channel.closeFuture().addListener(future -> metrics.connectionClosed());
                        initializer.accept(channel);
                    }
                });
    }
}
