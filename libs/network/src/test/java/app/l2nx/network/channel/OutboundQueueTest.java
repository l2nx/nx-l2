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
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.test.RecordingExecutor;
import app.l2nx.network.test.TestChannels;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class OutboundQueueTest {
    private final FlushCounter flushes = new FlushCounter();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NetworkMetrics metrics = new NetworkMetrics(registry);

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
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldCountTheDropOnItsOwnMetrics_whenTheChannelHasNoMetricsAttribute() {
            final EmbeddedChannel channel =
                    new EmbeddedChannel(new OutboundQueue(NetworkProperties.defaults(), metrics));
            channel.close();
            final ByteBuf frame = frame(1);

            OutboundQueue.of(channel).send(frame);

            assertThat(frame.refCnt()).isZero();
            assertThat(registry.get("l2nx.network.dropped")
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
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(2.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldReleaseFrames_whenTheEventLoopTerminatesWithTheDrainDropped() {
            final RecordingExecutor executor = new RecordingExecutor();
            final EmbeddedChannel channel = TestChannels.embedded();
            channel.pipeline().addLast(executor, new OutboundQueue(NetworkProperties.defaults(), metrics));
            final ByteBuf first = frame(1);
            final ByteBuf second = frame(2);
            executor.shutDown();

            OutboundQueue.of(channel).send(first);
            OutboundQueue.of(channel).send(second);
            assertThat(executor.pending())
                    .as("the drain was accepted, never run")
                    .isEqualTo(1);
            assertThat(first.refCnt()).isEqualTo(1);
            executor.terminate();

            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(2.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldWatchTheTerminationOnce_whenSeveralDrainsAreAcceptedDuringShutdown() {
            final RecordingExecutor executor = new RecordingExecutor();
            final EmbeddedChannel channel = TestChannels.embedded();
            channel.pipeline().addLast(executor, new OutboundQueue(NetworkProperties.defaults(), metrics));
            final OutboundQueue queue = OutboundQueue.of(channel);
            final ByteBuf second = frame(2);
            executor.shutDown();

            queue.send(frame(1));
            executor.runNext();
            queue.send(second);
            executor.terminate();

            assertThat(executor.terminationListeners()).isEqualTo(1);
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
            assertThat(registry.get("l2nx.network.unwritable").counter().count())
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

    @Nested
    class Handoff {
        private final RecordingExecutor executor = new RecordingExecutor();
        private final OutboundHandoff handoff = new OutboundHandoff();

        @Test
        void send_shouldQueueTheDrainWithoutWakeUp_whenTheHandoffIsOpen() {
            final EmbeddedChannel channel = onExecutor();
            final OutboundQueue queue = OutboundQueue.of(channel);
            handoff.open();

            queue.send(frame(1), handoff);
            queue.send(frame(2), handoff);

            assertThat(executor.lazy()).isEqualTo(1);
            assertThat(executor.immediate()).isZero();
            handoff.wake();
            assertThat(executor.immediate()).as("one wake-up").isEqualTo(1);
            executor.runAll();
            assertThat(readPayloads(channel, 2)).containsExactly(1, 2);
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldWakeASharedEventLoopOnce_whenSeveralConnectionsDefer() {
            final EmbeddedChannel first = onExecutor();
            final EmbeddedChannel second = onExecutor();
            handoff.open();

            OutboundQueue.of(first).send(frame(1), handoff);
            OutboundQueue.of(second).send(frame(2), handoff);
            handoff.wake();

            assertThat(executor.lazy()).isEqualTo(2);
            assertThat(executor.immediate()).isEqualTo(1);
            executor.runAll();
            assertThat(readPayloads(first, 1)).containsExactly(1);
            assertThat(readPayloads(second, 1)).containsExactly(2);
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }

        @Test
        void send_shouldWakeAtOnce_whenAnotherSenderFindsTheDrainDeferred() {
            final EmbeddedChannel channel = onExecutor();
            final OutboundQueue queue = OutboundQueue.of(channel);
            handoff.open();
            queue.send(frame(1), handoff);

            queue.send(frame(2));

            assertThat(executor.immediate())
                    .as("woken before the hand-off closes")
                    .isEqualTo(1);
            executor.runAll();
            assertThat(readPayloads(channel, 2)).containsExactly(1, 2);
            assertThat(flushes.count).as("one drain wrote both").isEqualTo(1);
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldWakeOnItsOwnHandoff_whenAnotherOpenHandoffDeferredTheDrain() {
            final EmbeddedChannel channel = onExecutor();
            final OutboundQueue queue = OutboundQueue.of(channel);
            final OutboundHandoff other = new OutboundHandoff();
            other.open();
            queue.send(frame(1), other);
            handoff.open();

            queue.send(frame(2), handoff);
            handoff.wake();

            assertThat(executor.lazy()).as("one deferred drain").isEqualTo(1);
            assertThat(executor.immediate())
                    .as("woken while the other hand-off stays open")
                    .isEqualTo(1);
            executor.runAll();
            assertThat(readPayloads(channel, 2)).containsExactly(1, 2);
            other.wake();
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldWakeAtOnce_whenTheHandoffIsClosed() {
            final EmbeddedChannel channel = onExecutor();

            OutboundQueue.of(channel).send(frame(1), handoff);

            assertThat(executor.lazy()).isZero();
            assertThat(executor.immediate()).isEqualTo(1);
            executor.runAll();
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldReleaseTheFrame_whenTheEventLoopRejectsTheDeferredDrain() {
            final EmbeddedChannel channel = onExecutor();
            final ByteBuf frame = frame(1);
            executor.reject();
            handoff.open();

            OutboundQueue.of(channel).send(frame, handoff);
            handoff.wake();

            assertThat(frame.refCnt()).isZero();
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "closed")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldReleaseTheFrame_whenTheChannelClosesBeforeTheWakeUp() {
            final EmbeddedChannel channel = onExecutor();
            final ByteBuf frame = frame(1);
            handoff.open();
            OutboundQueue.of(channel).send(frame, handoff);

            channel.pipeline().close();
            handoff.wake();
            executor.runAll();

            assertThat(frame.refCnt()).isZero();
            channel.finishAndReleaseAll();
        }

        @Test
        void drain_shouldReleaseTheFrame_whenTheEventLoopTerminatesWithTheDeferredDrainDropped() {
            final EmbeddedChannel channel = onExecutor();
            final ByteBuf frame = frame(1);
            executor.shutDown();
            handoff.open();

            OutboundQueue.of(channel).send(frame, handoff);
            handoff.wake();
            executor.terminate();

            assertThat(frame.refCnt()).isZero();
            channel.finishAndReleaseAll();
        }

        @Test
        void send_shouldCountEachDrainHandedToTheEventLoop() {
            final EmbeddedChannel channel = onExecutor();
            final OutboundQueue queue = OutboundQueue.of(channel);
            handoff.open();
            queue.send(frame(1), handoff);
            handoff.wake();
            executor.runAll();

            queue.send(frame(2));
            executor.runAll();

            assertThat(registry.get("l2nx.network.outbound.handoffs")
                            .tag("group", "io")
                            .counter()
                            .count())
                    .isEqualTo(2.0);
            channel.finishAndReleaseAll();
        }

        private EmbeddedChannel onExecutor() {
            final EmbeddedChannel channel = TestChannels.embedded(flushes);
            channel.pipeline().addLast(executor, new OutboundQueue(NetworkProperties.defaults(), metrics));
            return channel;
        }
    }

    private EmbeddedChannel channel(NetworkProperties properties) {
        return TestChannels.embedded(
                flushes,
                new FrameEncoder(FrameCipher.NONE, metrics),
                new OutboundQueue(properties, metrics),
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

    @ChannelHandler.Sharable
    private static final class FlushCounter extends ChannelOutboundHandlerAdapter {
        private int count;

        @Override
        public void flush(ChannelHandlerContext ctx) {
            count++;
            ctx.flush();
        }
    }
}
