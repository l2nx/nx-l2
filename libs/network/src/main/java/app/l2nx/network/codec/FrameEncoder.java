/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import app.l2nx.network.channel.Connections;
import app.l2nx.network.metrics.model.Direction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;

/**
 * A broadcast hands every session a {@link Frames#share} of one buffer, so a shared frame is copied before encryption;
 * the last holder encrypts in place.
 */
public final class FrameEncoder extends ChannelOutboundHandlerAdapter {
    private final FrameCipher cipher;

    public FrameEncoder(FrameCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.write(msg, promise);
            return;
        }

        final ByteBuf finished;
        try {
            finished = finish(ctx.alloc(), frame);
        } catch (RuntimeException e) {
            promise.setFailure(e);
            return;
        }
        Connections.metrics(ctx.channel()).bytes(Direction.OUT, finished.readableBytes());
        ctx.write(finished, promise);
    }

    /** Takes ownership of {@code frame}: it is released on every path except when it is returned. */
    ByteBuf finish(ByteBufAllocator alloc, ByteBuf frame) {
        ByteBuf target = null;
        try {
            final int payload = frame.readableBytes() - Frames.HEADER;
            if (payload < 0) {
                throw new IllegalArgumentException("Frame of " + frame.readableBytes() + " bytes has no header room");
            }

            final int encrypted = cipher.encryptedLength(payload);
            if ((Frames.HEADER + encrypted) > Frames.MAX_LENGTH) {
                throw new EncoderException(
                        "Frame of " + (Frames.HEADER + encrypted) + " bytes exceeds the length field");
            }
            if (Frames.isShared(frame) || frame.isReadOnly() || (frame.maxWritableBytes() < (encrypted - payload))) {
                target = alloc.buffer(Frames.HEADER + encrypted);
                target.writeBytes(frame, frame.readerIndex(), frame.readableBytes());
            } else {
                frame.ensureWritable(encrypted - payload);
                target = frame;
            }

            final int start = target.readerIndex();
            cipher.encrypt(target, start + Frames.HEADER, payload);
            target.writerIndex(start + Frames.HEADER + encrypted);
            target.setShortLE(start, Frames.HEADER + encrypted);
            if (target != frame) {
                frame.release();
            }
            return target;
        } catch (RuntimeException e) {
            if ((target != null) && (target != frame)) {
                target.release();
            }
            frame.release();
            throw e;
        }
    }
}
