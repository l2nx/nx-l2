/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.highfive.mapper;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * Turns one view event into one High Five packet on the emitting thread, without side effects. Viewer-dependent
 * output (relation, karma colour, language) declares it, so a broadcast serializes once per variant.
 */
public interface OutboundMapper<E extends ViewEvent> {
    Class<E> eventType();

    void write(E event, GameClient viewer, PacketWriter out);

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
