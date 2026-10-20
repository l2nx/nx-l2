/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Both timers count from accept. Reached-calls come from packet handlers on other threads and hop to the event
 * loop, which owns the timer fields.
 */
public final class ConnectionDeadlines extends ChannelInboundHandlerAdapter {
    private final long handshakeNanos;
    private final long authNanos;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> handshake;
    private ScheduledFuture<?> auth;

    public ConnectionDeadlines(Duration handshake, Duration auth) {
        handshakeNanos = handshake.toNanos();
        authNanos = auth.toNanos();
    }

    public static ConnectionDeadlines of(Channel channel) {
        return channel.pipeline().get(ConnectionDeadlines.class);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        if (ctx.channel().isActive()) {
            start();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        start();
        ctx.fireChannelActive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancel(handshake);
        cancel(auth);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancel(handshake);
        cancel(auth);
        ctx.fireChannelInactive();
    }

    public void handshakeReached() {
        EventLoopTasks.run(ctx.executor(), () -> cancel(handshake));
    }

    public void authReached() {
        EventLoopTasks.run(ctx.executor(), () -> {
            cancel(handshake);
            cancel(auth);
        });
    }

    private void start() {
        if (handshake != null) {
            return;
        }
        handshake = ctx.executor().schedule(this::expire, handshakeNanos, TimeUnit.NANOSECONDS);
        auth = ctx.executor().schedule(this::expire, authNanos, TimeUnit.NANOSECONDS);
    }

    private void expire() {
        Connections.close(ctx.channel(), CloseReason.TIMEOUT);
    }

    private static void cancel(ScheduledFuture<?> timer) {
        if (timer != null) {
            timer.cancel(false);
        }
    }
}
