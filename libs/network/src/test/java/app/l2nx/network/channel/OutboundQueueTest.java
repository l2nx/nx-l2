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
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class OutboundQueueTest {
    private final FlushCounter flushes = new FlushCounter();

    @Nested
    class Send {
        @Test
        void send_shouldWriteInOrderAndFlushOncePerDrain() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());
            final OutboundQueue queue = OutboundQueue.of(channel);

            queue.send(frame(1));
            queue.send(frame(2));
            queue.send(frame(3));
            channel.runPendingTasks();

            assertThat(readPayloads(channel, 3)).containsExactly(1, 2, 3);
            assertThat(flushes.count).isEqualTo(1);
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldCloseWithOutboundLimit_whenPendingBytesExceedLimit() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults().toBuilder()
                    .outboundLimit(DataSize.ofBytes(8))
                    .build());
            final ByteBuf big =
                    Frames.allocate(UnpooledByteBufAllocator.DEFAULT, 16).writeZero(16);

            OutboundQueue.of(channel).send(big);
            channel.runPendingTasks();

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.OUTBOUND_LIMIT);
            assertThat(big.refCnt()).isZero();
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldCountBytesWaitingInTheChannel_whenCheckingTheLimit() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults().toBuilder()
                    .outboundLimit(DataSize.ofBytes(24))
                    .build());
            channel.write(Frames.allocate(UnpooledByteBufAllocator.DEFAULT, 16).writeZero(16));

            OutboundQueue.of(channel).send(frame(1));

            assertThat(channel.isOpen()).as("closed by send, before any drain").isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.OUTBOUND_LIMIT);
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldReleaseAndCountDrop_whenClosing() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());
            channel.close();
            final ByteBuf frame = frame(1);

            OutboundQueue.of(channel).send(frame);
            channel.runPendingTasks();

            assertThat(frame.refCnt()).isZero();
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class Drain {
        @Test
        void drain_shouldWriteAtMostOneBatchPerRun() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());
            final OutboundQueue queue = OutboundQueue.of(channel);
            for (int i = 0; i < OutboundQueue.DRAIN_BATCH + 10; i++) {
                queue.send(frame(i));
            }

            channel.runPendingTasks();

            assertThat(channel.outboundMessages()).hasSize(OutboundQueue.DRAIN_BATCH + 10);
            assertThat(flushes.count).isEqualTo(2);
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldReleaseAndCountDrop_whenTheChannelClosedWithFramesQueued() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());
            final ByteBuf first = frame(1);
            final ByteBuf second = frame(2);
            OutboundQueue.of(channel).send(first);
            OutboundQueue.of(channel).send(second);

            // pipeline().close() skips the pending tasks that EmbeddedChannel.close() runs first, like an IO-thread
            // close.
            channel.pipeline().close();
            channel.runPendingTasks();

            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(2.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldReleaseFrames_whenTheEventLoopTerminatesWithTheDrainDropped() {
            final DroppingExecutor executor = new DroppingExecutor();
            final EmbeddedChannel channel = TestChannels.embedded();
            channel.pipeline().addLast(executor, new OutboundQueue(NetworkProperties.defaults()));
            final ByteBuf first = frame(1);
            final ByteBuf second = frame(2);
            executor.shutDown = true;

            OutboundQueue.of(channel).send(first);
            OutboundQueue.of(channel).send(second);
            assertThat(executor.accepted)
                    .as("the drain was accepted, never run")
                    .hasSize(1);
            assertThat(first.refCnt()).isEqualTo(1);
            executor.termination.setSuccess(null);

            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(2.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldWatchTheTerminationOnce_whenSeveralDrainsAreAcceptedDuringShutdown() {
            final DroppingExecutor executor = new DroppingExecutor();
            final EmbeddedChannel channel = TestChannels.embedded();
            channel.pipeline().addLast(executor, new OutboundQueue(NetworkProperties.defaults()));
            final OutboundQueue queue = OutboundQueue.of(channel);
            final ByteBuf second = frame(2);
            executor.shutDown = true;

            queue.send(frame(1));
            executor.accepted.removeFirst().run();
            queue.send(second);
            executor.termination.setSuccess(null);

            assertThat(executor.terminationListeners).hasValue(1);
            assertThat(second.refCnt()).isZero();
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class SendAndClose {
        @Test
        void sendAndClose_shouldWriteFrameThenCloseAndReleaseLaterFrames() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());
            final OutboundQueue queue = OutboundQueue.of(channel);
            final ByteBuf late = frame(9);

            final ChannelFuture closed = queue.sendAndClose(frame(7));
            queue.send(late);
            channel.runPendingTasks();

            assertThat(closed.isDone()).isTrue();
            assertThat(channel.isOpen()).isFalse();
            assertThat(readPayloads(channel, 1)).containsExactly(7);
            assertThat(late.refCnt()).isZero();
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class ChannelWritabilityChanged {
        @Test
        void writability_shouldCloseSlowClient_afterSlowClientTimeout() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());

            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
            // User-defined writability changes fire through a task on the event loop.
            channel.runPendingTasks();
            channel.advanceTimeBy(9, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            assertThat(channel.isOpen()).isTrue();
            channel.advanceTimeBy(1, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.SLOW);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.unwritable")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void writability_shouldCancelSlowClientTimer_whenWritableAgain() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());

            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
            channel.runPendingTasks();
            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true);
            channel.runPendingTasks();
            channel.advanceTimeBy(30, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();

            assertThat(channel.isOpen()).isTrue();
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldReleaseQueuedFrames() {
            final EmbeddedChannel channel = channel(NetworkProperties.defaults());
            final ByteBuf first = frame(1);
            final ByteBuf second = frame(2);
            OutboundQueue.of(channel).send(first);
            OutboundQueue.of(channel).send(second);

            OutboundQueue.of(channel).close(CloseReason.ERROR);
            channel.runPendingTasks();

            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();
            channel.finishAndReleaseAll();
        }
    }

    private EmbeddedChannel channel(NetworkProperties properties) {
        return TestChannels.embedded(
                flushes,
                new FrameEncoder(FrameCipher.NONE),
                new OutboundQueue(properties),
                TailExceptionHandler.INSTANCE);
    }

    private static ByteBuf frame(int value) {
        return Frames.allocate(UnpooledByteBufAllocator.DEFAULT, 4).writeIntLE(value);
    }

    private static List<Integer> readPayloads(EmbeddedChannel channel, int count) {
        final List<Integer> payloads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final ByteBuf written = channel.readOutbound();
            payloads.add(written.getIntLE(written.readerIndex() + Frames.HEADER));
            written.release();
        }
        return payloads;
    }

    private static final class FlushCounter extends ChannelOutboundHandlerAdapter {
        private int count;

        @Override
        public void flush(ChannelHandlerContext ctx) {
            count++;
            ctx.flush();
        }
    }

    /**
     * Accepts every task and runs none, as a terminating event loop does with a task offered after its last pass.
     * Reports itself in the event loop so the handler is added inline.
     */
    private static final class DroppingExecutor extends AbstractEventExecutor {
        private final List<Runnable> accepted = new ArrayList<>();
        private final AtomicInteger terminationListeners = new AtomicInteger();
        private final Promise<Void> termination = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE) {
            @Override
            public Promise<Void> addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
                terminationListeners.incrementAndGet();
                return super.addListener(listener);
            }
        };
        private boolean shutDown;

        @Override
        public void execute(Runnable task) {
            accepted.add(task);
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return true;
        }

        @Override
        public boolean isShuttingDown() {
            return shutDown;
        }

        @Override
        public boolean isShutdown() {
            return shutDown;
        }

        @Override
        public boolean isTerminated() {
            return termination.isDone();
        }

        @Override
        public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            shutDown = true;
            return termination;
        }

        @Override
        public Future<?> terminationFuture() {
            return termination;
        }

        @Override
        @Deprecated
        public void shutdown() {
            shutDown = true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return termination.isDone();
        }
    }
}
