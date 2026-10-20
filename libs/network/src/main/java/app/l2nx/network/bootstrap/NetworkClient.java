/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

public final class NetworkClient {
    private NetworkClient() {}

    public static ChannelFuture connect(
            InetSocketAddress address,
            NetworkEventLoops loops,
            NetworkProperties properties,
            NetworkMetrics metrics,
            Consumer<Channel> initializer) {
        return new Bootstrap()
                .group(loops.workers())
                .channel(loops.transport().socketChannelClass())
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
                })
                .connect(address);
    }
}
