/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.clientpackets.LoginClientPacket;
import app.l2nx.login.protocol.c621.clientpackets.LoginClientPackets;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.RateLimitedLog;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses on the IO thread and hands the packet to the connection's lane, where it may block (RSA, database). Login
 * never reaches the game, so every error decoding a packet or handing it on closes the connection.
 */
@Slf4j
public final class LoginPacketDecoder extends ChannelInboundHandlerAdapter {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final LoginClient client = LoginClient.of(ctx.channel());
        final NetworkMetrics metrics = Connections.metrics(ctx.channel());
        final ConnectionState state = client.getConnectionState();
        try {
            final LoginClientPacket packet;
            try {
                // The frame decoder may still emit frames of the same read after a close.
                packet = ctx.channel().isActive() ? decode(client, metrics, state, frame) : null;
            } finally {
                frame.release();
            }
            if (packet != null) {
                client.execute(packet);
            }
        } catch (RuntimeException e) {
            metrics.decodeError(state.name());
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
    private static LoginClientPacket decode(
            LoginClient client, NetworkMetrics metrics, ConnectionState state, ByteBuf frame) {
        final int opcode = frame.readUnsignedByte();
        final LoginClientPackets entry =
                opcode < LoginClientPackets.PACKET_ARRAY.length ? LoginClientPackets.PACKET_ARRAY[opcode] : null;
        if (entry == null) {
            metrics.dropped(DropReason.UNKNOWN_OPCODE);
            if (RATE.tryAcquire("login-unknown")) {
                log.debug("Unknown login opcode 0x{} from {}", Integer.toHexString(opcode), client);
            }
            return null;
        }
        if (!entry.getConnectionStates().contains(state)) {
            metrics.dropped(DropReason.WRONG_STATE);
            return null;
        }
        if (entry.isIgnored()) {
            metrics.dropped(DropReason.IGNORED);
            return null;
        }
        final LoginClientPacket packet = entry.newPacket();
        return packet.decode(client, frame) ? packet : null;
    }
}
