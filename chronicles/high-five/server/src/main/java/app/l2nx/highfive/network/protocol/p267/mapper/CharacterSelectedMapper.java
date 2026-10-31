/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.CharacterSelected;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.CharSelected;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class CharacterSelectedMapper implements OutboundMapper<CharacterSelected> {
    @Override
    public Class<CharacterSelected> eventType() {
        return CharacterSelected.class;
    }

    @Override
    public GameServerPacket map(CharacterSelected event, GameClient viewer) {
        return CharSelected.builder()
                .name(event.name())
                .objectId(event.playerHandle())
                .title(event.title())
                .playOk1(event.playOk1())
                .clanId(event.clanId())
                .female(event.female())
                .race(event.race().ordinal())
                .classId(event.classId())
                .x(event.x())
                .y(event.y())
                .z(event.z())
                .currentHp(event.currentHp())
                .currentMp(event.currentMp())
                // The client reads a 32-bit SP here, as the legacy packet wrote it.
                .sp((int) event.sp())
                .exp(event.exp())
                .level(event.level())
                .karma(event.karma())
                .pkKills(event.pkKills())
                .intelligence(event.intelligence())
                .strength(event.strength())
                .constitution(event.constitution())
                .mentality(event.mentality())
                .dexterity(event.dexterity())
                .wit(event.wit())
                .gameMinute(event.gameMinute())
                .build();
    }

    @Override
    public int sizeHint() {
        return 256;
    }
}
