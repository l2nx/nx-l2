/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.highfive;

import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.highfive.network.protocol.OpcodeTable;
import app.l2nx.highfive.network.protocol.highfive.mapper.HighFiveMappers;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPackets;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class HighFiveAdapter implements GameProtocolAdapter {
    public static final String ID = "highfive";

    private static final int KEY_FRAME_SIZE = 24;
    private static final int PING_FRAME_SIZE = 5;

    private final OpcodeTable inbound = HighFiveInbound.build();

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
        final ByteBuf frame = Frames.allocate(alloc, KEY_FRAME_SIZE);
        KeyPacketWriter.write(new PacketWriter(frame), key, true);
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
        ServerPackets.SERVER_CLOSE.writeId(new PacketWriter(frame));
        return frame;
    }

    @Override
    public OpcodeTable.Entry lookup(ByteBuf frame) {
        return inbound.lookup(frame);
    }

    @Override
    public ByteBuf serialize(ViewEvent event, GameClient viewer, ByteBufAllocator alloc) {
        return HighFiveMappers.serialize(event, viewer, alloc);
    }

    @Override
    public ByteBuf serializeLegacy(LegacyPacket packet, GameClient viewer, ByteBufAllocator alloc) {
        return ((ServerPacket) packet).serialize(viewer, alloc);
    }

    @Override
    public Object audienceKey(ViewEvent event, GameClient viewer) {
        return HighFiveMappers.audienceKey(this, event, viewer);
    }

    @Override
    public List<OpcodeTable.Route> inboundRoutes() {
        return inbound.routes();
    }

    @Override
    public Set<Class<? extends ViewEvent>> mappedEvents() {
        return HighFiveMappers.mapped();
    }

    @Override
    public Set<Class<? extends ViewEvent>> droppedEvents() {
        return HighFiveMappers.dropped();
    }

    @Override
    public int legacyInboundCount() {
        return inbound.legacyCount();
    }
}
