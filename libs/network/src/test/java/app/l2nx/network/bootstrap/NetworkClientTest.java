/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.config.Transport;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.ResolvedTransport;
import app.l2nx.network.transport.TransportResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NetworkClientTest {
    private final ResolvedTransport transport =
            TransportResolver.nativeAvailability().resolve(Transport.NIO);
    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport.ioHandlerFactory());
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NetworkMetrics metrics = new NetworkMetrics(registry);

    @AfterEach
    void tearDown() {
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Nested
    class ConnectOnGroup {
        @Test
        void connect_shouldOpenTheChannelOnTheGivenLoop_withTheTransportsChannelClass() throws Exception {
            final EventLoop loop = group.next();
            final AtomicReference<Thread> initializedOn = new AtomicReference<>();
            try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                final ChannelFuture future = NetworkClient.connect(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()),
                        loop,
                        transport,
                        Duration.ofMillis(1_500),
                        NetworkProperties.defaults(),
                        metrics,
                        channel -> initializedOn.set(Thread.currentThread()));
                try (Socket accepted = server.accept()) {
                    final Channel channel = future.syncUninterruptibly().channel();

                    assertThat(channel).isInstanceOf(NioSocketChannel.class);
                    assertThat(channel.eventLoop()).isSameAs(loop);
                    assertThat(initializedOn.get()).isNotNull();
                    assertThat(channel.attr(NetworkMetrics.KEY).get()).isSameAs(metrics);
                    assertThat(channel.config().getOption(ChannelOption.TCP_NODELAY))
                            .isTrue();
                    assertThat(channel.config().getConnectTimeoutMillis()).isEqualTo(1_500);
                    assertThat(registry.get("l2nx.network.connections").gauge().value())
                            .isEqualTo(1.0);

                    channel.close().syncUninterruptibly();
                }
            }
            assertThat(registry.get("l2nx.network.connections").gauge().value()).isZero();
        }

        @ParameterizedTest(name = "{0} ms")
        @ValueSource(longs = {0, -1})
        void connect_shouldReject_whenTheConnectTimeoutIsNotPositive(long millis) {
            final InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 1);
            final Duration timeout = Duration.ofMillis(millis);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> NetworkClient.connect(
                            address, group, transport, timeout, NetworkProperties.defaults(), metrics, channel -> {}))
                    .withMessageContaining("connect timeout");
        }
    }
}
