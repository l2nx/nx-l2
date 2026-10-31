/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedXaddArrayQueue;

/**
 * Sole writer of its channel, because the game's rolling XOR depends on write order. Multi-producer, single-consumer:
 * only the holder of the consumer role polls, and it hands the role back before re-checking the queue so a frame
 * offered meanwhile is never left behind.
 */
public final class OutboundQueue extends ChannelDuplexHandler {
    public static final AttributeKey<OutboundQueue> KEY = AttributeKey.valueOf("l2nx.network.outbound");
    public static final int DRAIN_BATCH = 64;

    private static final int CHUNK_SIZE = 64;
    private static final int IDLE = 0;
    private static final int SUBMITTED = 1;
    private static final int ACTIVE = 2;

    private final MessagePassingQueue<ByteBuf> queue = new MpscUnboundedXaddArrayQueue<>(CHUNK_SIZE);
    private final AtomicInteger role = new AtomicInteger(IDLE);
    private final AtomicBoolean terminationWatched = new AtomicBoolean();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final long outboundLimit;
    private final long slowClientNanos;
    private final Runnable drainTask = this::drain;
    private volatile ChannelHandlerContext ctx;
    private volatile boolean closing;
    // Set by close(reason): queued frames are released, not written, even by a drain scheduled before the close.
    private volatile boolean aborted;
    private volatile ByteBuf closeAfter;
    private ScheduledFuture<?> slowClientTimer;

    public OutboundQueue(NetworkProperties properties) {
        outboundLimit = properties.outboundLimit().toBytes();
        slowClientNanos = properties.slowClientTimeout().toNanos();
    }

    public static OutboundQueue of(Channel channel) {
        return channel.attr(KEY).get();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        ctx.channel().attr(KEY).set(this);
        // Not channelInactive: a client channel that never connects closes without becoming inactive.
        ctx.channel().closeFuture().addListener(future -> onClosed());
    }

    private void onClosed() {
        closing = true;
        // A pending drain releases the rest itself: it sees the channel closed.
        if (role.compareAndSet(IDLE, ACTIVE)) {
            discardQueued();
        }
    }

    public void send(ByteBuf frame) {
        if (closing) {
            frame.release();
            Connections.metrics(ctx.channel()).dropped(DropReason.CLOSED);
            return;
        }
        enqueue(frame);
    }

    public ChannelFuture sendAndClose(ByteBuf frame) {
        final Channel channel = ctx.channel();
        if (closing) {
            frame.release();
            Connections.metrics(channel).dropped(DropReason.CLOSED);
            return channel.closeFuture();
        }
        closeAfter = frame;
        closing = true;
        enqueue(frame);
        return channel.closeFuture();
    }

    public void close(CloseReason reason) {
        aborted = true;
        closing = true;
        Connections.close(ctx.channel(), reason);
    }

    public boolean isClosing() {
        return closing;
    }

    public long pendingBytes() {
        return queuedBytes.get() + channelPending(ctx.channel());
    }

    private void enqueue(ByteBuf frame) {
        final long pending = queuedBytes.addAndGet(frame.readableBytes()) + channelPending(ctx.channel());
        queue.offer(frame);
        if (pending > outboundLimit) {
            close(CloseReason.OUTBOUND_LIMIT);
        }
        schedule();
    }

    // The outbound buffer keeps its total in a volatile field, so any producer thread reads the live value.
    private static long channelPending(Channel channel) {
        final ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        return buffer == null ? 0 : buffer.totalPendingWriteBytes();
    }

    // Never writes inline, even on the event loop: encoding the key packet switches inbound decryption on, and clear
    // frames decoded later in the same channelRead must still pass in clear.
    private void schedule() {
        if (!role.compareAndSet(IDLE, SUBMITTED)) {
            return;
        }
        final EventExecutor executor = ctx.executor();
        try {
            executor.execute(drainTask);
        } catch (RejectedExecutionException e) {
            // The event loop will never drain. Only a termination listener can have taken the role meanwhile.
            if (role.compareAndSet(SUBMITTED, ACTIVE)) {
                discardQueued();
            }
            return;
        }
        if (executor.isShutdown() && terminationWatched.compareAndSet(false, true)) {
            // A task accepted while the loop terminates may be dropped unrun; the termination future completes after
            // that, so one listener reclaims whatever was dropped.
            executor.terminationFuture().addListener(future -> reclaimAfterTermination());
        }
    }

    private void reclaimAfterTermination() {
        if (role.compareAndSet(SUBMITTED, ACTIVE) || role.compareAndSet(IDLE, ACTIVE)) {
            discardQueued();
        }
    }

    /** Only for the holder of the role: releases what is queued and hands the role back. */
    private void discardQueued() {
        do {
            releaseQueued();
            role.set(IDLE);
        } while (!queue.isEmpty() && role.compareAndSet(IDLE, ACTIVE));
    }

    private void drain() {
        if (!role.compareAndSet(SUBMITTED, ACTIVE)) {
            return;
        }
        final Channel channel = ctx.channel();
        if (!aborted && channel.isOpen() && !channel.isActive()) {
            // Still connecting: the frames wait for channelActive, which runs on this loop after this drain.
            role.set(IDLE);
            return;
        }
        int written = 0;
        ByteBuf frame;
        while ((written < DRAIN_BATCH) && ((frame = queue.poll()) != null)) {
            queuedBytes.addAndGet(-frame.readableBytes());
            if (aborted || !channel.isOpen()) {
                frame.release();
                Connections.metrics(channel).dropped(DropReason.CLOSED);
                continue;
            }
            if (frame == closeAfter) {
                ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
                discardQueued();
                return;
            }
            ctx.write(frame, ctx.voidPromise());
            written++;
        }

        if (written > 0) {
            ctx.flush();
        }
        if ((queuedBytes.get() + channelPending(channel)) > outboundLimit) {
            close(CloseReason.OUTBOUND_LIMIT);
        }
        role.set(IDLE);
        if (!queue.isEmpty()) {
            schedule();
        }
    }

    private void releaseQueued() {
        final NetworkMetrics metrics = Connections.metrics(ctx.channel());
        ByteBuf frame;
        while ((frame = queue.poll()) != null) {
            queuedBytes.addAndGet(-frame.readableBytes());
            frame.release();
            metrics.dropped(DropReason.CLOSED);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (!ctx.channel().isWritable()) {
            Connections.metrics(ctx.channel()).unwritable();
            if (slowClientTimer == null) {
                slowClientTimer = ctx.executor()
                        .schedule(
                                () -> {
                                    slowClientTimer = null;
                                    if (!ctx.channel().isWritable()) {
                                        close(CloseReason.SLOW);
                                    }
                                },
                                slowClientNanos,
                                TimeUnit.NANOSECONDS);
            }
        } else if (slowClientTimer != null) {
            slowClientTimer.cancel(false);
            slowClientTimer = null;
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closing = true;
        if (slowClientTimer != null) {
            slowClientTimer.cancel(false);
            slowClientTimer = null;
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!queue.isEmpty()) {
            schedule();
        }
        ctx.fireChannelActive();
    }
}
