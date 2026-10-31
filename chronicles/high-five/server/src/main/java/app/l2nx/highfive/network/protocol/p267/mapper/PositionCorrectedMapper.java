/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.movement.view.PositionCorrected;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.ValidateLocation;

public final class PositionCorrectedMapper implements OutboundMapper<PositionCorrected> {
    @Override
    public Class<PositionCorrected> eventType() {
        return PositionCorrected.class;
    }

    @Override
    public GameServerPacket map(PositionCorrected event, GameClient viewer) {
        return ValidateLocation.builder()
                .objectId(event.handle())
                .x(event.x())
                .y(event.y())
                .z(event.z())
                .heading(event.heading())
                .build();
    }

    @Override
    public int sizeHint() {
        return 21;
    }
}
