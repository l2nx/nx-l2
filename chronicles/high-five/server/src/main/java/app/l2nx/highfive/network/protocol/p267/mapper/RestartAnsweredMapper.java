/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.RestartAnswered;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.RestartResponse;

public final class RestartAnsweredMapper implements OutboundMapper<RestartAnswered> {
    @Override
    public Class<RestartAnswered> eventType() {
        return RestartAnswered.class;
    }

    @Override
    public GameServerPacket map(RestartAnswered event, GameClient viewer) {
        return new RestartResponse(event.accepted());
    }

    @Override
    public int sizeHint() {
        return 5;
    }
}
