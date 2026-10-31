/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

/**
 * Maps one view event to one wire record of the p267 protocol on the emitting thread, without side effects; the
 * record writes the layout. Viewer-dependent output (relation, karma colour, language) declares it, so a broadcast
 * serializes once per variant.
 */
public interface OutboundMapper<E extends ViewEvent> {
    Class<E> eventType();

    /** {@code null} when this viewer's client gets nothing for the event. */
    GameServerPacket map(E event, GameClient viewer);

    default boolean viewerDependent() {
        return false;
    }

    /** Viewers with equal variants get equal bytes; used only when {@link #viewerDependent()}. */
    default Object variant(E event, GameClient viewer) {
        return viewer;
    }

    default int sizeHint() {
        return 64;
    }
}
