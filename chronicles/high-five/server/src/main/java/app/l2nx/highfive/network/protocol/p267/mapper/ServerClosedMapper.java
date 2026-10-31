/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.ServerClose;

public final class ServerClosedMapper implements OutboundMapper<ServerClosed> {
    @Override
    public Class<ServerClosed> eventType() {
        return ServerClosed.class;
    }

    @Override
    public GameServerPacket map(ServerClosed event, GameClient viewer) {
        return ServerClose.INSTANCE;
    }

    @Override
    public int sizeHint() {
        return 1;
    }
}
