/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.WorldLeft;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.LeaveWorld;

public final class WorldLeftMapper implements OutboundMapper<WorldLeft> {
    @Override
    public Class<WorldLeft> eventType() {
        return WorldLeft.class;
    }

    @Override
    public GameServerPacket map(WorldLeft event, GameClient viewer) {
        return LeaveWorld.INSTANCE;
    }

    @Override
    public int sizeHint() {
        return 1;
    }
}
