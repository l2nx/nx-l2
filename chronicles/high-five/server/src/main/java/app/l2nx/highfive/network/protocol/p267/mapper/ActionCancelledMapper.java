/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.ActionFailed;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class ActionCancelledMapper implements OutboundMapper<ActionCancelled> {
    @Override
    public Class<ActionCancelled> eventType() {
        return ActionCancelled.class;
    }

    @Override
    public GameServerPacket map(ActionCancelled event, GameClient viewer) {
        return ActionFailed.INSTANCE;
    }

    @Override
    public int sizeHint() {
        return 1;
    }
}
