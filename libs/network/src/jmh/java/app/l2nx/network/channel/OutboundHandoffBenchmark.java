/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * One frame to each of N loopback connections until every server event loop wrote it: a wake-up per connection
 * (no hand-off) against one per event loop (hand-off closed after the fan-out).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class OutboundHandoffBenchmark {
    @Param({"50", "200", "1000"})
    private int connections;

    @Param({"4"})
    private int eventLoops;

    @Param({"false", "true"})
    private boolean handoff;

    private final List<OutboundQueue> queues = new CopyOnWriteArrayList<>();
    private final List<Channel> clients = new ArrayList<>();
    private final AtomicLong written = new AtomicLong();
    private final OutboundHandoff passHandoff = new OutboundHandoff();
    private EventLoopGroup serverLoops;
    private EventLoopGroup clientLoops;
    private Channel server;
    private long expected;

    @Setup
    public void setUp() throws InterruptedException {
        serverLoops = new MultiThreadIoEventLoopGroup(eventLoops, NioIoHandler.newFactory());
        clientLoops = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        final NetworkMetrics metrics = NetworkMetrics.noop();
        server = new ServerBootstrap()
                .group(serverLoops)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        final OutboundQueue queue = new OutboundQueue(NetworkProperties.defaults(), metrics);
                        ch.pipeline()
                                .addLast(new WriteCounter(written), new FrameEncoder(FrameCipher.NONE, metrics), queue);
                        queues.add(queue);
                    }
                })
                .bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .sync()
                .channel();
        final Bootstrap bootstrap = new Bootstrap()
                .group(clientLoops)
                .channel(NioSocketChannel.class)
                .handler(Discard.INSTANCE);
        for (int i = 0; i < connections; i++) {
            clients.add(bootstrap.connect(server.localAddress()).sync().channel());
        }
        while (queues.size() < connections) {
            Thread.onSpinWait();
        }
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        for (Channel client : clients) {
            client.close().sync();
        }
        server.close().sync();
        serverLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        clientLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
    }

    @Benchmark
    public void fanOut() {
        final OutboundHandoff passed = handoff ? passHandoff : null;
        if (handoff) {
            passHandoff.open();
        }
        for (int i = 0; i < queues.size(); i++) {
            queues.get(i)
                    .send(Frames.allocate(PooledByteBufAllocator.DEFAULT, 8).writeLongLE(i), passed);
        }
        if (handoff) {
            passHandoff.wake();
        }
        expected += queues.size();
        while (written.get() < expected) {
            Thread.onSpinWait();
        }
    }

    private static final class WriteCounter extends ChannelOutboundHandlerAdapter {
        private final AtomicLong written;

        WriteCounter(AtomicLong written) {
            this.written = written;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ctx.write(msg, promise);
            written.incrementAndGet();
        }
    }

    @ChannelHandler.Sharable
    private static final class Discard extends ChannelInboundHandlerAdapter {
        private static final Discard INSTANCE = new Discard();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }
    }
}
