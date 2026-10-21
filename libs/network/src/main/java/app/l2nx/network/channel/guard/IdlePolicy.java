/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.config.NetworkProperties;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Read-idle past {@code read-timeout} closes; after {@link #startPinging}, each {@code ping-interval} idle pings and
 * {@code ping-misses} unanswered pings close. Netty idle times are fixed, so switching replaces the {@code idle}
 * handler.
 */
public final class IdlePolicy extends ChannelInboundHandlerAdapter {
    private final Duration readTimeout;
    private final Duration pingInterval;
    private final int pingMisses;
    private ChannelHandlerContext ctx;
    private Runnable ping;
    private int misses;

    private IdlePolicy(NetworkProperties properties) {
        readTimeout = properties.readTimeout();
        pingInterval = properties.pingInterval();
        pingMisses = properties.pingMisses();
    }

    public static void install(ChannelPipeline pipeline, NetworkProperties properties) {
        pipeline.addLast(PipelineNames.IDLE, readIdle(properties.readTimeout()));
        pipeline.addLast(PipelineNames.IDLE_POLICY, new IdlePolicy(properties));
    }

    public static IdlePolicy of(Channel channel) {
        return channel.pipeline().get(IdlePolicy.class);
    }

    public void startPinging(Runnable ping) {
        EventLoopTasks.run(ctx.executor(), () -> switchTo(ping, pingInterval));
    }

    public void stopPinging() {
        EventLoopTasks.run(ctx.executor(), () -> switchTo(null, readTimeout));
    }

    /**
     * For a phase in which the client legitimately stays silent longer than {@code read-timeout}; read-idle still
     * closes, at the given bound.
     */
    public void extendReadTimeout(Duration readTimeout) {
        EventLoopTasks.run(ctx.executor(), () -> switchTo(null, readTimeout));
    }

    private void switchTo(Runnable newPing, Duration readerIdle) {
        ping = newPing;
        misses = 0;
        if (ctx.channel().isOpen()) {
            ctx.pipeline().replace(PipelineNames.IDLE, PipelineNames.IDLE, readIdle(readerIdle));
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        misses = 0;
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (!(evt instanceof IdleStateEvent idle) || (idle.state() != IdleState.READER_IDLE)) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        if ((ping == null) || (misses >= pingMisses)) {
            Connections.close(ctx.channel(), CloseReason.IDLE);
            return;
        }
        misses++;
        ping.run();
    }

    private static IdleStateHandler readIdle(Duration time) {
        return new IdleStateHandler(time.toNanos(), 0, 0, TimeUnit.NANOSECONDS);
    }
}
