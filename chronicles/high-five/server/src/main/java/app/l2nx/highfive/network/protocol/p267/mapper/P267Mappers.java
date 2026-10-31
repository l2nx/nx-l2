/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267Adapter;
import app.l2nx.network.codec.Frames;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class P267Mappers {
    private static final List<OutboundMapper<?>> MAPPERS = List.of(
            new ActionCancelledMapper(),
            new MessageShownMapper(),
            new AuthenticationFailedMapper(),
            new CharacterListShownMapper(),
            new CharacterSelectedMapper(),
            new CharacterCreatedMapper(),
            new CharacterCreateFailedMapper(),
            new CharacterDeletedMapper(),
            new CharacterDeleteFailedMapper(),
            new ServerClosedMapper(),
            new WorldLeftMapper(),
            new RestartAnsweredMapper(),
            new PositionCorrectedMapper(),
            new ChatMessageMapper());

    private static final Set<Class<? extends ViewEvent>> DROPPED = Set.of();

    private static final Map<Class<?>, OutboundMapper<?>> BY_TYPE =
            MAPPERS.stream().collect(Collectors.toUnmodifiableMap(OutboundMapper::eventType, Function.identity()));

    private P267Mappers() {}

    public static Set<Class<? extends ViewEvent>> mapped() {
        return MAPPERS.stream()
                .<Class<? extends ViewEvent>>map(OutboundMapper::eventType)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<Class<? extends ViewEvent>> dropped() {
        return DROPPED;
    }

    @SuppressWarnings("unchecked")
    public static ByteBuf serialize(ViewEvent event, GameClient viewer, ByteBufAllocator alloc) {
        final OutboundMapper<ViewEvent> mapper = (OutboundMapper<ViewEvent>) BY_TYPE.get(event.getClass());
        if (mapper == null) {
            if (DROPPED.contains(event.getClass())) {
                return null;
            }
            throw new IllegalStateException(
                    "No p267 mapper for " + event.getClass().getName());
        }
        // Mapped before allocating, so a viewer whose client gets nothing costs no buffer.
        final GameServerPacket packet = mapper.map(event, viewer);
        if (packet == null) {
            return null;
        }
        return Frames.write(alloc, mapper.sizeHint(), frame -> {
            packet.write(frame);
            return true;
        });
    }

    @SuppressWarnings("unchecked")
    public static Object audienceKey(P267Adapter adapter, ViewEvent event, GameClient viewer) {
        final OutboundMapper<ViewEvent> mapper = (OutboundMapper<ViewEvent>) BY_TYPE.get(event.getClass());
        if (mapper == null) {
            return null;
        }
        return mapper.viewerDependent() ? List.of(adapter, mapper.variant(event, viewer)) : adapter;
    }
}
