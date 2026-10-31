/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.metrics.model.Direction;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CipherDecoder extends ChannelInboundHandlerAdapter {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private final FrameCipher cipher;

    public CipherDecoder(FrameCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Connections.metrics(ctx.channel()).bytes(Direction.IN, Frames.HEADER + frame.readableBytes());
        if (!frame.isReadable()) {
            // A frame of only its length is a client keep-alive.
            frame.release();
            return;
        }

        ByteBuf writable = frame;
        if (frame.isReadOnly()) {
            writable = ctx.alloc().buffer(frame.readableBytes()).writeBytes(frame);
            frame.release();
        }

        final boolean accepted;
        try {
            accepted = cipher.decrypt(writable, writable.readerIndex(), writable.readableBytes());
        } catch (RuntimeException e) {
            writable.release();
            throw e;
        }

        if (!accepted) {
            if (RATE.tryAcquire("cipher-rejected")) {
                log.warn(
                        "Connection {} closed: a frame of {} bytes failed the {} check",
                        ctx.channel().remoteAddress(),
                        writable.readableBytes(),
                        cipher.getClass().getSimpleName());
            }
            writable.release();
            Connections.close(ctx.channel(), CloseReason.ERROR);
            return;
        }

        ctx.fireChannelRead(writable);
    }
}
