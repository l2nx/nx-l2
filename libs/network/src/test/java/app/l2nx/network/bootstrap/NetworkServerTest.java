/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.channel.guard.ConnectionLimiter;
import app.l2nx.network.codec.CipherDecoder;
import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.config.Transport;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.network.transport.TransportResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NetworkServerTest {
    private final NetworkProperties properties = NetworkProperties.defaults().toBuilder()
            .transport(Transport.NIO)
            .workerThreads(1)
            .connectionsPerIp(1)
            .shutdownQuietPeriod(Duration.ZERO)
            .shutdownTimeout(Duration.ofSeconds(2))
            .build();
    private final NetworkEventLoops loops = new NetworkEventLoops(properties, TransportResolver.nativeAvailability());
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NetworkMetrics metrics = new NetworkMetrics(registry);
    private NetworkServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
        loops.close();
    }

    @Test
    void bind_shouldEchoFramesThroughTheStandardPipeline() throws Exception {
        server = bind();

        try (Socket socket = new Socket(
                InetAddress.getLoopbackAddress(), server.localAddress().getPort())) {
            final OutputStream out = socket.getOutputStream();
            out.write(new byte[] {5, 0, 1, 2, 3});
            out.flush();

            final byte[] actual = new byte[5];
            new DataInputStream(socket.getInputStream()).readFully(actual);

            assertThat(actual).containsExactly(5, 0, 1, 2, 3);
            assertThat(registry.get("l2nx.network.connections").gauge().value()).isEqualTo(1.0);
        }
    }

    @Test
    void bind_shouldRejectSecondConnectionFromSameAddress_whenLimitIsOne() throws Exception {
        server = bind();
        final int port = server.localAddress().getPort();

        try (Socket first = new Socket(InetAddress.getLoopbackAddress(), port);
                Socket second = new Socket(InetAddress.getLoopbackAddress(), port)) {
            assertThat(second.getInputStream().read()).isEqualTo(-1);
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "connections-per-ip")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(first.isClosed()).isFalse();
        }
    }

    private NetworkServer bind() {
        return NetworkServer.bind(
                "echo",
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                loops,
                properties,
                metrics,
                new ConnectionLimiter(properties, address -> false),
                NetworkServerTest::echoPipeline);
    }

    private static void echoPipeline(Channel channel) {
        channel.pipeline()
                .addLast(PipelineNames.FRAME_DECODER, Frames.decoder(16 * 1024))
                .addLast(PipelineNames.CIPHER_DECODER, new CipherDecoder(FrameCipher.NONE))
                .addLast(PipelineNames.FRAME_ENCODER, new FrameEncoder(FrameCipher.NONE))
                .addLast(PipelineNames.OUTBOUND, new OutboundQueue(NetworkProperties.defaults()))
                .addLast(PipelineNames.PACKET, new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
                        final ByteBuf echo = Frames.allocate(ctx.alloc(), frame.readableBytes())
                                .writeBytes(frame);
                        OutboundQueue.of(ctx.channel()).send(echo);
                    }
                })
                .addLast(PipelineNames.TAIL, TailExceptionHandler.INSTANCE);
    }
}
