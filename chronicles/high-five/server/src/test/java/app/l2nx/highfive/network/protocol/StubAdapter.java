/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.List;
import java.util.Set;

public class StubAdapter implements GameProtocolAdapter {
    public static final int SERVER_CLOSE = 0x20;
    public static final int BAD_PRE_AUTH = 0x03;
    public static final int BAD_AUTHED = 0x08;
    /** A server bug: its decoder returns neither a command nor a legacy packet. */
    public static final int NEITHER = 0x09;

    public record Probe(int value) implements Command {}

    public record Shown(int value) implements ViewEvent {}

    private final String id;
    private final int version;
    private final int legacyCount;
    private final OpcodeTable table = OpcodeTable.builder(0xD0)
            .preAuthCommand(0x01, Probe.class, (frame, c) -> new Probe(frame.readIntLE()))
            .command(0x02, Probe.class, (frame, c) -> new Probe(frame.readIntLE()))
            .preAuthRoute(BAD_PRE_AUTH, RouteKind.LEGACY, StubAdapter::fail)
            .route(0x04, RouteKind.IGNORE, (frame, c) -> OpcodeTable.IGNORED)
            .route(0x05, RouteKind.CONNECTION, (frame, c) -> OpcodeTable.CONSUMED)
            .route(BAD_AUTHED, RouteKind.LEGACY, StubAdapter::fail)
            .preAuthRoute(NEITHER, RouteKind.LEGACY, (frame, c) -> "neither")
            .exCommand(0x10, Probe.class, (frame, c) -> new Probe(frame.readIntLE()))
            .build();

    public StubAdapter(String id, int version, int legacyCount) {
        this.id = id;
        this.version = version;
        this.legacyCount = legacyCount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean accepts(int protocolVersion) {
        return protocolVersion == version;
    }

    @Override
    public ByteBuf keyFrame(ByteBufAllocator alloc, byte[] key) {
        return Frames.allocate(alloc, 9).writeByte(0x2E).writeBytes(key, 0, 8);
    }

    @Override
    public ByteBuf pingFrame(ByteBufAllocator alloc, int pingId) {
        return Frames.allocate(alloc, 5).writeByte(0xD9).writeIntLE(pingId);
    }

    @Override
    public ByteBuf serverCloseFrame(ByteBufAllocator alloc) {
        return Frames.allocate(alloc, 1).writeByte(SERVER_CLOSE);
    }

    @Override
    public OpcodeTable.Entry lookup(ByteBuf frame) {
        return table.lookup(frame);
    }

    private static Object fail(ByteBuf frame, GameConnection connection) {
        throw new PacketDecodeException("bad");
    }

    @Override
    public ByteBuf serialize(ViewEvent event, GameClient viewer, ByteBufAllocator alloc) {
        return event instanceof Shown shown
                ? Frames.allocate(alloc, 5).writeByte(0x77).writeIntLE(shown.value())
                : null;
    }

    @Override
    public ByteBuf serializeLegacy(LegacyPacket packet, GameClient viewer, ByteBufAllocator alloc) {
        return null;
    }

    @Override
    public List<OpcodeTable.Route> inboundRoutes() {
        return table.routes();
    }

    @Override
    public Set<Class<? extends ViewEvent>> mappedEvents() {
        return Set.of(Shown.class);
    }

    @Override
    public Set<Class<? extends ViewEvent>> droppedEvents() {
        return Set.of();
    }

    @Override
    public int legacyInboundCount() {
        return legacyCount;
    }
}
