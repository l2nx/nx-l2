/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.character.view.CharacterListShown;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.CharSelectionInfo;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.ArrayList;
import java.util.List;

public final class CharacterListShownMapper implements OutboundMapper<CharacterListShown> {
    private static final int[] PAPERDOLL_ORDER = ServerPacket.paperdollOrder();
    // The client draws no enchant glow above this value.
    private static final int MAX_ENCHANT_EFFECT = 127;
    private static final int BANNED = -1;

    @Override
    public Class<CharacterListShown> eventType() {
        return CharacterListShown.class;
    }

    @Override
    public GameServerPacket map(CharacterListShown event, GameClient viewer) {
        final List<CharacterSummary> characters = event.characters();
        final long now = ClockBridge.wallMillis();
        final List<CharSelectionInfo.Slot> slots = new ArrayList<>(characters.size());
        for (int i = 0; i < characters.size(); i++) {
            slots.add(slot(event, characters.get(i), i == event.activeSlot(), now));
        }
        // 0 keeps the client from offering character creation.
        return new CharSelectionInfo(ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT, slots);
    }

    @Override
    public int sizeHint() {
        return 1_024;
    }

    private static CharSelectionInfo.Slot slot(
            CharacterListShown event, CharacterSummary character, boolean active, long now) {
        final List<Integer> items = new ArrayList<>(PAPERDOLL_ORDER.length);
        for (int slot : PAPERDOLL_ORDER) {
            items.add(character.paperdollItemId(slot));
        }
        return CharSelectionInfo.Slot.builder()
                .name(character.name())
                .objectId(character.objectId())
                .login(event.login())
                .playOk1(event.playOk1())
                .clanId(character.clanId())
                .sex(character.sex().ordinal())
                .race(character.race().ordinal())
                .baseClassId(character.baseClassId())
                .x(character.x())
                .y(character.y())
                .z(character.z())
                .currentHp(character.currentHp())
                .currentMp(character.currentMp())
                .sp((int) character.sp())
                .exp(character.exp())
                .expPercent(expPercent(character))
                .level(character.level())
                .karma(character.karma())
                .pkKills(character.pkKills())
                .pvpKills(character.pvpKills())
                .paperdollItemIds(items)
                .hairStyle(character.hairStyle())
                .hairColor(character.hairColor())
                .face(character.face())
                .maxHp(character.maxHp())
                .maxMp(character.maxMp())
                .deleteSeconds(deleteSeconds(character, now))
                .classId(character.classId())
                .active(active)
                .enchantEffect(Math.min(character.enchantEffect(), MAX_ENCHANT_EFFECT))
                .augmentationId(character.augmentationId())
                .vitalityPoints(character.vitalityPoints())
                .build();
    }

    // Single precision, as the client's own computation: the bytes stay the ones the legacy packet wrote.
    private static double expPercent(CharacterSummary character) {
        final ExperienceData experience = ExperienceData.getInstance();
        final long levelExp = experience.getExpForLevel(character.level());
        return (float) (character.exp() - levelExp) / (experience.getExpForLevel(character.level() + 1) - levelExp);
    }

    private static int deleteSeconds(CharacterSummary character, long now) {
        if (character.accessLevel() < 0) {
            return BANNED;
        }
        return (character.deleteTimer() > 0) ? (int) ((character.deleteTimer() - now) / 1000) : 0;
    }
}
