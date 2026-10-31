/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.login.network.LoginClient;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.RateLimitedLog;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes by opcode alone on the IO thread; the handler runs on the connection's lane because it may block (RSA,
 * database). Any decode or hand-off error closes the connection.
 */
@Slf4j
@RequiredArgsConstructor
public final class LoginPacketDecoder extends ChannelInboundHandlerAdapter {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private final LoginRoutes routes;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final LoginClient client = LoginClient.of(ctx.channel());
        final NetworkMetrics metrics = Connections.metrics(ctx.channel());
        try {
            final LanePacket packet;
            try {
                // The frame decoder may still emit frames of the same read after a close.
                packet = ctx.channel().isActive() ? decode(client, metrics, frame) : null;
            } finally {
                frame.release();
            }
            if (packet != null) {
                client.execute(packet);
            }
        } catch (RuntimeException e) {
            metrics.decodeError(client.getConnectionState().name());
            if (RATE.tryAcquire("login-decode")) {
                if (TailExceptionHandler.isExpected(e)) {
                    log.warn("Login decode error from {}: {}", client, e.toString());
                } else {
                    log.warn("Login decode error from {}", client, e);
                }
            }
            Connections.close(ctx.channel(), CloseReason.ERROR);
        }
    }

    /**
     * @return the decoded packet, {@code null} when it was dropped
     */
    private LanePacket decode(LoginClient client, NetworkMetrics metrics, ByteBuf frame) {
        final int opcode = frame.readUnsignedByte();
        final LoginClientOpcode known = LoginClientOpcode.of(opcode);
        final LoginRoute route = known == null ? null : routes.route(known);
        if (route == null) {
            metrics.dropped(DropReason.UNKNOWN_OPCODE);
            if (RATE.tryAcquire("login-unknown")) {
                log.debug("Unknown login opcode 0x{} from {}", Integer.toHexString(opcode), client);
            }
            return null;
        }
        if (route.isIgnored()) {
            metrics.dropped(DropReason.IGNORED);
            return null;
        }
        return LanePacket.builder()
                .client(client)
                .handler(route.decode(frame))
                .accepted(route.accepted())
                .build();
    }
}
