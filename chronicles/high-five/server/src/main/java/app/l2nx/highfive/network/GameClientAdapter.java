/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.network.channel.OutboundHandoff;
import app.l2nx.network.metrics.model.Direction;
import io.netty.buffer.ByteBuf;

/**
 * Serializes through the connection's adapter on the calling thread; before the handshake chose an adapter there is
 * nothing a client could read, so output is dropped.
 */
public final class GameClientAdapter implements ClientPort {
    private final GameConnection connection;

    public GameClientAdapter(GameConnection connection) {
        this.connection = connection;
    }

    @Override
    public void emit(ViewEvent event) {
        final ByteBuf frame = serialize(event);
        if (frame != null) {
            connection.outbound().send(frame, handoff());
        }
    }

    @Override
    public void emitAndClose(ViewEvent event) {
        final ByteBuf frame = serialize(event);
        if (frame == null) {
            connection.channel().close();
            return;
        }
        connection.outbound().sendAndClose(frame);
    }

    @Override
    public void legacy(LegacyPacket packet) {
        final ByteBuf frame = serializeLegacy(packet);
        if (frame != null) {
            connection.outbound().send(frame, handoff());
        }
    }

    @Override
    public void legacyAndClose(LegacyPacket packet) {
        final ByteBuf frame = serializeLegacy(packet);
        if (frame == null) {
            connection.channel().close();
            return;
        }
        connection.outbound().sendAndClose(frame);
    }

    private static OutboundHandoff handoff() {
        final Loop loop = Loop.current();
        return loop == null ? null : loop.networkHandoff().outbound();
    }

    private ByteBuf serialize(ViewEvent event) {
        final GameProtocolAdapter adapter = connection.adapter();
        return adapter == null ? null : adapter.serialize(event, connection.client(), connection.alloc());
    }

    private ByteBuf serializeLegacy(LegacyPacket packet) {
        final GameProtocolAdapter adapter = connection.adapter();
        if (adapter == null) {
            return null;
        }
        connection.metrics().legacyPacket(packet.getClass(), Direction.OUT);
        return adapter.serializeLegacy(packet, connection.client(), connection.alloc());
    }
}
