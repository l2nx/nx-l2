/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.highfive.network.protocol.OpcodeTable;
import app.l2nx.highfive.network.protocol.p267.mapper.P267Mappers;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPackets;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketWriter;
import app.l2nx.protocol.game.p267.serverpackets.KeyPacket;
import app.l2nx.protocol.game.p267.serverpackets.ServerClose;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class P267Adapter implements GameProtocolAdapter {
    public static final String ID = "p267";

    private static final int KEY_FRAME_SIZE = 24;
    private static final int PING_FRAME_SIZE = 5;

    private final OpcodeTable inbound = P267Inbound.build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean accepts(int protocolVersion) {
        return switch (protocolVersion) {
            case 267, 268, 271, 273 -> true;
            default -> false;
        };
    }

    @Override
    public ByteBuf keyFrame(ByteBufAllocator alloc, byte[] key) {
        final KeyPacket packet = KeyPacket.builder()
                .accepted(true)
                .key(Arrays.copyOf(key, KeyPacket.KEY_BYTES))
                .serverId(ServerConfig.SERVER_ID)
                .build();
        final ByteBuf frame = Frames.allocate(alloc, KEY_FRAME_SIZE);
        packet.write(frame);
        return frame;
    }

    @Override
    public ByteBuf pingFrame(ByteBufAllocator alloc, int pingId) {
        final ByteBuf frame = Frames.allocate(alloc, PING_FRAME_SIZE);
        final PacketWriter out = new PacketWriter(frame);
        ServerPackets.NET_PING.writeId(out);
        out.writeInt(pingId);
        return frame;
    }

    @Override
    public ByteBuf serverCloseFrame(ByteBufAllocator alloc) {
        final ByteBuf frame = Frames.allocate(alloc, 1);
        ServerClose.INSTANCE.write(frame);
        return frame;
    }

    @Override
    public OpcodeTable.Entry lookup(ByteBuf frame) {
        return inbound.lookup(frame);
    }

    @Override
    public ByteBuf serialize(ViewEvent event, GameClient viewer, ByteBufAllocator alloc) {
        return P267Mappers.serialize(event, viewer, alloc);
    }

    @Override
    public ByteBuf serializeLegacy(LegacyPacket packet, GameClient viewer, ByteBufAllocator alloc) {
        return ((ServerPacket) packet).serialize(viewer, alloc);
    }

    @Override
    public List<OpcodeTable.Route> inboundRoutes() {
        return inbound.routes();
    }

    @Override
    public Set<Class<? extends ViewEvent>> mappedEvents() {
        return P267Mappers.mapped();
    }

    @Override
    public Set<Class<? extends ViewEvent>> droppedEvents() {
        return P267Mappers.dropped();
    }

    @Override
    public int legacyInboundCount() {
        return inbound.legacyCount();
    }
}
