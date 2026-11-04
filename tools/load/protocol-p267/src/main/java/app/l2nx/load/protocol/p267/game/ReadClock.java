/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.LongSupplier;

/**
 * First in the pipeline, ahead of the frame decoder: the frames cut out of one read are handled one after the other, so
 * the time from this stamp to a frame's handling shows the queue that inbound traffic builds on the event loop.
 */
final class ReadClock extends ChannelInboundHandlerAdapter {
    static final String NAME = "read-clock";

    private final LongSupplier clock;
    private long readAt;

    ReadClock(LongSupplier clock) {
        this.clock = clock;
    }

    long nanosSinceRead() {
        return clock.getAsLong() - readAt;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        readAt = clock.getAsLong();
        ctx.fireChannelRead(msg);
    }
}
