/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.CharacterDeleted;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.CharDeleteSuccess;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class CharacterDeletedMapper implements OutboundMapper<CharacterDeleted> {
    @Override
    public Class<CharacterDeleted> eventType() {
        return CharacterDeleted.class;
    }

    @Override
    public GameServerPacket map(CharacterDeleted event, GameClient viewer) {
        return CharDeleteSuccess.INSTANCE;
    }

    @Override
    public int sizeHint() {
        return 1;
    }
}
