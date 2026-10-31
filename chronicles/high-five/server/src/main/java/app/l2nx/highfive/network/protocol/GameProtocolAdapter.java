/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.List;
import java.util.Set;

/**
 * Every frame it returns follows the frame contract of {@code Frames} (header room first) and is owned by the
 * caller.
 */
public interface GameProtocolAdapter {
    String id();

    boolean accepts(int protocolVersion);

    /** The first outbound frame of a connection, sent in clear. */
    ByteBuf keyFrame(ByteBufAllocator alloc, byte[] key);

    ByteBuf pingFrame(ByteBufAllocator alloc, int pingId);

    ByteBuf serverCloseFrame(ByteBufAllocator alloc);

    /**
     * IO thread: the route of the frame's opcode, whatever the session state; the frame is left at the packet body.
     *
     * @return {@code null} for an unknown opcode
     */
    OpcodeTable.Entry lookup(ByteBuf frame);

    /** {@code null} when the event is dropped for this client. */
    ByteBuf serialize(ViewEvent event, GameClient viewer, ByteBufAllocator alloc);

    ByteBuf serializeLegacy(LegacyPacket packet, GameClient viewer, ByteBufAllocator alloc);

    List<OpcodeTable.Route> inboundRoutes();

    Set<Class<? extends ViewEvent>> mappedEvents();

    Set<Class<? extends ViewEvent>> droppedEvents();

    int legacyInboundCount();
}
