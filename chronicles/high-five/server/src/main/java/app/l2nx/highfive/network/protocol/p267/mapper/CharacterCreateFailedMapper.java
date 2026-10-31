/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.CharacterCreateFailed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateFail;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharCreateFailReason;

public final class CharacterCreateFailedMapper implements OutboundMapper<CharacterCreateFailed> {
    @Override
    public Class<CharacterCreateFailed> eventType() {
        return CharacterCreateFailed.class;
    }

    @Override
    public GameServerPacket map(CharacterCreateFailed event, GameClient viewer) {
        return new CharCreateFail(
                switch (event.reason()) {
                    case CREATION_FAILED -> CharCreateFailReason.REASON_CREATION_FAILED;
                    case TOO_MANY_CHARACTERS -> CharCreateFailReason.REASON_TOO_MANY_CHARACTERS;
                    case NAME_ALREADY_EXISTS -> CharCreateFailReason.REASON_NAME_ALREADY_EXISTS;
                    // The High Five client's only length message speaks of 16 characters.
                    case NAME_TOO_LONG -> CharCreateFailReason.REASON_16_ENG_CHARS;
                    case INCORRECT_NAME -> CharCreateFailReason.REASON_INCORRECT_NAME;
                });
    }

    @Override
    public int sizeHint() {
        return 5;
    }
}
