/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.metrics.model.DropReason;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes use {@code voidPromise()}, so a failed write surfaces only here.
 */
@Slf4j
@Sharable
public final class TailExceptionHandler extends ChannelInboundHandlerAdapter {
    public static final TailExceptionHandler INSTANCE = new TailExceptionHandler();

    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private TailExceptionHandler() {}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        final String type = cause.getClass().getSimpleName();
        if (RATE.tryAcquire(type)) {
            if (cause instanceof IOException) {
                // A peer resetting the connection is routine (a client closed the game).
                log.debug("Connection {} failed: {}", ctx.channel().remoteAddress(), cause.toString());
            } else if (isExpected(cause)) {
                log.warn("Connection {} closed on {}: {}", ctx.channel().remoteAddress(), type, cause.getMessage());
            } else {
                log.warn("Connection {} closed on {}", ctx.channel().remoteAddress(), type, cause);
            }
        }
        Connections.close(ctx.channel(), CloseReason.ERROR);
    }

    /**
     * A peer's I/O failure or malformed input says nothing about our code, so its message is enough; anything else
     * is a bug whose stack trace is needed.
     */
    public static boolean isExpected(Throwable cause) {
        return (cause instanceof IOException) || (cause instanceof DecoderException);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ReferenceCountUtil.release(msg);
        Connections.metrics(ctx.channel()).dropped(DropReason.UNHANDLED);
    }
}
