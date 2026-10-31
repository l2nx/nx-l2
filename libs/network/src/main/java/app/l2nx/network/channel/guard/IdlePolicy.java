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

/** Netty idle times are fixed, so every switch of mode replaces the {@code idle} handler. */
public final class IdlePolicy extends ChannelInboundHandlerAdapter {
    private final Duration pingInterval;
    private final int pingMisses;
    private ChannelHandlerContext ctx;
    private Runnable ping;
    private Runnable farewell;
    private int misses;

    private IdlePolicy(NetworkProperties properties) {
        pingInterval = properties.pingInterval();
        pingMisses = properties.pingMisses();
    }

    public static void install(ChannelPipeline pipeline, NetworkProperties properties) {
        pipeline.addLast(PipelineNames.IDLE, readIdle(properties.readTimeout()));
        pipeline.addLast(PipelineNames.IDLE_POLICY, new IdlePolicy(properties));
    }

    public static void startPinging(Channel channel, Runnable ping) {
        final IdlePolicy idle = channel.pipeline().get(IdlePolicy.class);
        if (idle != null) {
            EventLoopTasks.run(channel.eventLoop(), () -> idle.switchTo(ping, null, idle.pingInterval));
        }
    }

    /**
     * {@code farewell} sends the protocol's last packet and closes, because a client may not notice a bare close; if
     * the connection is still open at the next read-idle, it is closed bare.
     */
    public static void extendReadTimeout(Channel channel, Duration readTimeout, Runnable farewell) {
        final IdlePolicy idle = channel.pipeline().get(IdlePolicy.class);
        if (idle != null) {
            EventLoopTasks.run(channel.eventLoop(), () -> idle.switchTo(null, farewell, readTimeout));
        }
    }

    private void switchTo(Runnable newPing, Runnable newFarewell, Duration readerIdle) {
        ping = newPing;
        farewell = newFarewell;
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
        if (farewell != null) {
            final Runnable last = farewell;
            farewell = null;
            Connections.closing(ctx.channel(), CloseReason.IDLE);
            last.run();
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
