/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

/**
 * Runs on the emitting thread and must be free of side effects, except recording the session-local mail numbers it
 * issues ({@link P267MailNumbers}).
 */
public interface OutboundMapper<E extends ViewEvent> {
    Class<E> eventType();

    /** {@code null} when this viewer's client gets nothing for the event. */
    GameServerPacket map(E event, GameClient viewer);

    default int sizeHint() {
        return 64;
    }
}
