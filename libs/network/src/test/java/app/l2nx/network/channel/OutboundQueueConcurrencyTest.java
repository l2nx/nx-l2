/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboundQueueConcurrencyTest {
    private static final int PRODUCERS = 4;
    private static final int FRAMES = 2_000;

    @Nested
    class Send {
        @Test
        void send_shouldKeepEachProducersOrder_whenManyThreadsSend() throws Exception {
            final EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, LocalIoHandler.newFactory());
            final LocalAddress address = new LocalAddress("outbound-queue-test");
            final AtomicReference<OutboundQueue> serverQueue = new AtomicReference<>();
            final CountDownLatch connected = new CountDownLatch(1);
            final List<Long> received = new CopyOnWriteArrayList<>();
            final CountDownLatch done = new CountDownLatch(PRODUCERS * FRAMES);
            try {
                new ServerBootstrap()
                        .group(group)
                        .channel(LocalServerChannel.class)
                        .childHandler(new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                final OutboundQueue queue = new OutboundQueue(NetworkProperties.defaults());
                                ch.pipeline().addLast(new FrameEncoder(FrameCipher.NONE), queue);
                                serverQueue.set(queue);
                                connected.countDown();
                            }
                        })
                        .bind(address)
                        .sync();
                final Channel client = new Bootstrap()
                        .group(group)
                        .channel(LocalChannel.class)
                        .handler(new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline().addLast(Frames.decoder(1024), new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
                                        received.add(frame.readLongLE());
                                        done.countDown();
                                    }
                                });
                            }
                        })
                        .connect(address)
                        .sync()
                        .channel();
                assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

                final Thread[] producers = new Thread[PRODUCERS];
                for (int p = 0; p < PRODUCERS; p++) {
                    final long producer = p;
                    producers[p] = Thread.ofPlatform().start(() -> {
                        for (int i = 0; i < FRAMES; i++) {
                            serverQueue
                                    .get()
                                    .send(Frames.allocate(client.alloc(), 8).writeLongLE((producer << 32) | i));
                        }
                    });
                }
                for (Thread producer : producers) {
                    producer.join();
                }

                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
                for (long producer = 0; producer < PRODUCERS; producer++) {
                    final long p = producer;
                    assertThat(received.stream()
                                    .filter(v -> (v >>> 32) == p)
                                    .map(v -> v & 0xFFFFFFFFL)
                                    .toList())
                            .isSorted()
                            .hasSize(FRAMES);
                }
                client.close().sync();
            } finally {
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            }
        }

        @Test
        void send_shouldHoldFramesUntilTheChannelConnects() throws Exception {
            final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
            final LocalAddress address = new LocalAddress("outbound-connect");
            final List<Integer> received = new CopyOnWriteArrayList<>();
            final CountDownLatch done = new CountDownLatch(3);
            try {
                new ServerBootstrap()
                        .group(group)
                        .channel(LocalServerChannel.class)
                        .childHandler(new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline().addLast(Frames.decoder(1024), new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
                                        received.add(frame.readIntLE());
                                        done.countDown();
                                    }
                                });
                            }
                        })
                        .bind(address)
                        .sync();
                final Channel client = unconnectedClient(group);
                final List<ByteBuf> frames = sendFrames(client, 3);

                assertThat(client.isActive()).isFalse();
                assertThat(frames)
                        .allSatisfy(frame -> assertThat(frame.refCnt()).isEqualTo(1));

                client.connect(address).sync();

                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsExactly(0, 1, 2);
                client.close().sync();
            } finally {
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            }
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldReleaseEveryFrame_whenTheEventLoopStopsWithFramesQueued() throws Exception {
            final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
            final LocalAddress address = new LocalAddress("outbound-shutdown");
            final OutboundQueue queue = new OutboundQueue(NetworkProperties.defaults());
            new ServerBootstrap()
                    .group(group)
                    .channel(LocalServerChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(address)
                    .sync();
            final Channel client = new Bootstrap()
                    .group(group)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new FrameEncoder(FrameCipher.NONE), queue);
                        }
                    })
                    .connect(address)
                    .sync()
                    .channel();
            final CountDownLatch blocked = new CountDownLatch(1);
            final CountDownLatch resume = new CountDownLatch(1);
            client.eventLoop().execute(() -> {
                blocked.countDown();
                awaitUninterruptibly(resume);
            });
            assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();

            final List<ByteBuf> frames = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                // Unpooled: a released pooled buffer is recycled for other allocations, so its refCnt proves nothing.
                final ByteBuf frame =
                        Frames.allocate(UnpooledByteBufAllocator.DEFAULT, 4).writeIntLE(i);
                frames.add(frame);
                queue.send(frame);
                if (i == 99) {
                    // The drain is queued behind the blocker; the loop shuts down before it runs.
                    group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
                    resume.countDown();
                }
            }
            group.terminationFuture().sync();

            assertThat(frames).allSatisfy(frame -> assertThat(frame.refCnt()).isZero());
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldReleaseHeldFrames_whenTheChannelNeverConnects() throws Exception {
            final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
            try {
                final Channel client = unconnectedClient(group);
                final List<ByteBuf> frames = sendFrames(client, 2);

                client.close().sync();
                client.eventLoop().submit(() -> {}).sync();

                assertThat(frames)
                        .allSatisfy(frame -> assertThat(frame.refCnt()).isZero());
            } finally {
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            }
        }
    }

    private static Channel unconnectedClient(EventLoopGroup group) throws InterruptedException {
        return new Bootstrap()
                .group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(
                                        new FrameEncoder(FrameCipher.NONE),
                                        new OutboundQueue(NetworkProperties.defaults()));
                    }
                })
                .register()
                .sync()
                .channel();
    }

    /** Sends unpooled frames and waits until the drain they scheduled has run on the loop. */
    private static List<ByteBuf> sendFrames(Channel channel, int count) throws InterruptedException {
        final List<ByteBuf> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final ByteBuf frame =
                    Frames.allocate(UnpooledByteBufAllocator.DEFAULT, 4).writeIntLE(i);
            frames.add(frame);
            OutboundQueue.of(channel).send(frame);
        }
        channel.eventLoop().submit(() -> {}).sync();
        return frames;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
