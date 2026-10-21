/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.pipeline;

import app.l2nx.engine.core.session.Session;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Sharable
public final class GameSessionHandler extends ChannelInboundHandlerAdapter {
    public static final GameSessionHandler INSTANCE = new GameSessionHandler();

    private GameSessionHandler() {}

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!GameConnection.of(ctx.channel()).session().offer((Session.Input) msg)) {
            Connections.close(ctx.channel(), CloseReason.FLOOD);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        GameConnection.of(ctx.channel()).session().close();
        ctx.fireChannelInactive();
    }
}
