/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.pipeline;

import app.l2nx.engine.core.session.SessionState;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.RateLimitedLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the first frame (protocol version) and sets the connection's adapter. Later frames pass through, unless the
 * first was refused: then nothing more is decoded while the connection closes.
 */
@Slf4j
public final class VersionHandshakeDecoder extends ChannelInboundHandlerAdapter {
    private static final int PROTOCOL_VERSION = 0x0E;
    // Clients probe a server with this version and close at once; it is not worth a log line.
    private static final int PING_VERSION = -2;
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private final GameProtocolRegistry registry;
    private final Supplier<byte[]> keys;
    private boolean refused;

    public VersionHandshakeDecoder(GameProtocolRegistry registry, Supplier<byte[]> keys) {
        this.registry = registry;
        this.keys = keys;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (refused) {
            ReferenceCountUtil.release(msg);
            return;
        }
        final GameConnection connection = GameConnection.of(ctx.channel());
        if ((connection.adapter() != null) || !(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final int version;
        try {
            if (!frame.isReadable() || (frame.readUnsignedByte() != PROTOCOL_VERSION)) {
                refused = true;
                Connections.metrics(ctx.channel()).decodeError(SessionState.CONNECTED.name());
                Connections.close(ctx.channel(), CloseReason.ERROR);
                return;
            }
            if (frame.readableBytes() < 4) {
                if (RATE.tryAcquire("short-version")) {
                    log.info(
                            "Refused a version frame of {} bytes from {}",
                            frame.readableBytes() + 1,
                            ctx.channel().remoteAddress());
                }
                refuse(ctx, connection);
                return;
            }
            version = frame.readIntLE();
        } finally {
            frame.release();
        }

        if (version == PING_VERSION) {
            refused = true;
            ctx.close();
            return;
        }

        final Optional<GameProtocolAdapter> adapter = registry.find(version);
        if (adapter.isEmpty()) {
            if (RATE.tryAcquire("unknown-version")) {
                log.info(
                        "Refused protocol version {} from {}",
                        version,
                        ctx.channel().remoteAddress());
            }
            refuse(ctx, connection);
            return;
        }

        connection.accept(adapter.get(), version);
        final byte[] key = keys.get();
        connection.cipher().setKey(key);
        connection.outbound().send(adapter.get().keyFrame(ctx.alloc(), key));
    }

    private void refuse(ChannelHandlerContext ctx, GameConnection connection) {
        refused = true;
        connection.outbound().sendAndClose(HandshakeRefusal.frame(ctx.alloc()));
    }
}
