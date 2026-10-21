/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.login.network.LoginClient;
import app.l2nx.protocol.c621.serverpackets.Init;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * {@code Init} is the only frame encrypted with the static Blowfish key.
 */
public final class LoginHandshakeHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final LoginClient client = LoginClient.of(ctx.channel());
        client.sendPacket(Init.builder()
                .sessionId(client.getSessionId())
                .scrambledModulus(client.getScrambledModulus())
                .blowfishKey(client.getBlowfishKey())
                .build());
        ctx.pipeline().remove(this);
        ctx.fireChannelActive();
    }
}
