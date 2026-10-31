/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.CharacterCreated;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateOk;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class CharacterCreatedMapper implements OutboundMapper<CharacterCreated> {
    @Override
    public Class<CharacterCreated> eventType() {
        return CharacterCreated.class;
    }

    @Override
    public GameServerPacket map(CharacterCreated event, GameClient viewer) {
        return CharCreateOk.INSTANCE;
    }

    @Override
    public int sizeHint() {
        return 5;
    }
}
