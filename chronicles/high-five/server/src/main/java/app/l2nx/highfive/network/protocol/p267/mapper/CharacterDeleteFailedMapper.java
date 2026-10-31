/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.CharacterDeleteFailed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.CharDeleteFail;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharDeleteFailReason;

public final class CharacterDeleteFailedMapper implements OutboundMapper<CharacterDeleteFailed> {
    @Override
    public Class<CharacterDeleteFailed> eventType() {
        return CharacterDeleteFailed.class;
    }

    @Override
    public GameServerPacket map(CharacterDeleteFailed event, GameClient viewer) {
        return new CharDeleteFail(
                switch (event.reason()) {
                    case CLAN_MEMBER -> CharDeleteFailReason.REASON_YOU_MAY_NOT_DELETE_CLAN_MEMBER;
                    case CLAN_LEADER -> CharDeleteFailReason.REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED;
                });
    }

    @Override
    public int sizeHint() {
        return 5;
    }
}
