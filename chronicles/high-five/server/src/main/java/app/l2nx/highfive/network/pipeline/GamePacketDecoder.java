/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.pipeline;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.OpcodeTable;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.Direction;
import app.l2nx.network.metrics.model.DropReason;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * Before authentication only pre-auth routes are read. The state may be stale by one input (the login queued earlier in
 * the same read); game-state checks happen when the packet runs, never here.
 */
@Slf4j
@Sharable
public final class GamePacketDecoder extends ChannelInboundHandlerAdapter {
    public static final GamePacketDecoder INSTANCE = new GamePacketDecoder();

    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private GamePacketDecoder() {}

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final GameConnection connection = GameConnection.of(ctx.channel());
        final NetworkMetrics metrics = Connections.metrics(ctx.channel());
        final SessionState state = connection.session().state();
        final int opcode = frame.getUnsignedByte(frame.readerIndex());
        OpcodeTable.Entry entry = null;
        final Object input;
        try {
            // The frame decoder may still emit frames of the same read after a close.
            if (!ctx.channel().isActive()) {
                return;
            }
            entry = connection.adapter().lookup(frame);
            if (entry == null) {
                unknown(ctx, metrics, opcode, state);
                return;
            }
            if ((state == SessionState.CONNECTED) && !entry.preAuth()) {
                metrics.dropped(DropReason.UNAUTHENTICATED);
                if (RATE.tryAcquire("unauthenticated-" + opcode)) {
                    log.debug(
                            "Opcode 0x{} before authentication from {}",
                            Integer.toHexString(opcode),
                            ctx.channel().remoteAddress());
                }
                return;
            }
            input = entry.decoder().decode(frame, connection);
        } catch (RuntimeException e) {
            decodeError(ctx, connection, metrics, opcode, state, entry, e);
            return;
        } finally {
            frame.release();
        }

        if (input == OpcodeTable.UNKNOWN) {
            unknown(ctx, metrics, opcode, state);
            return;
        }
        if (input == OpcodeTable.IGNORED) {
            metrics.dropped(DropReason.IGNORED);
            return;
        }
        if (input == OpcodeTable.CONSUMED) {
            return;
        }
        // Outside the decode's try: a decoder returning neither a command nor a legacy packet is a server bug, which
        // the tail handler logs with its stack, not a client's decode error.
        final Session.Input item = connection.session().input(input);
        if (input instanceof Command command) {
            metrics.command(command.getClass());
        } else {
            metrics.legacyPacket(input.getClass(), Direction.IN);
        }
        ctx.fireChannelRead(item);
    }

    private static void unknown(ChannelHandlerContext ctx, NetworkMetrics metrics, int opcode, SessionState state) {
        metrics.dropped(DropReason.UNKNOWN_OPCODE);
        if (RATE.tryAcquire("unknown-" + opcode)) {
            log.debug(
                    "Unknown opcode 0x{} in {} from {}",
                    Integer.toHexString(opcode),
                    state,
                    ctx.channel().remoteAddress());
        }
    }

    /**
     * Only in-game packets tolerate decode errors up to the limit; before the game a malformed packet ends the session.
     * {@code entry} is {@code null} when the opcode itself could not be read.
     */
    private static void decodeError(
            ChannelHandlerContext ctx,
            GameConnection connection,
            NetworkMetrics metrics,
            int opcode,
            SessionState state,
            OpcodeTable.Entry entry,
            RuntimeException e) {
        metrics.decodeError(state.name());
        if (RATE.tryAcquire("decode-" + opcode)) {
            log.info(
                    "Decode error of opcode 0x{} in {} from {}: {}",
                    Integer.toHexString(opcode),
                    state,
                    ctx.channel().remoteAddress(),
                    e.toString());
        }
        final boolean authenticated = (entry != null) && !entry.preAuth() && (state == SessionState.AUTHED);
        if (!authenticated || !connection.allowDecodeError()) {
            Connections.close(ctx.channel(), CloseReason.ERROR);
        }
    }
}
