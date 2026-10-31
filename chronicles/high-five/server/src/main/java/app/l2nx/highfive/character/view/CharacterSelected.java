/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import lombok.Builder;

@Builder(toBuilder = true)
public record CharacterSelected(
        String name,
        int playerHandle,
        String title,
        int playOk1,
        int clanId,
        boolean female,
        Race race,
        int classId,
        int x,
        int y,
        int z,
        double currentHp,
        double currentMp,
        long sp,
        long exp,
        int level,
        int karma,
        int pkKills,
        int intelligence,
        int strength,
        int constitution,
        int mentality,
        int dexterity,
        int wit,
        int gameMinute)
        implements ViewEvent {
    public static CharacterSelected of(Player player, int playOk1, int gameMinute) {
        return builder()
                .name(player.getName())
                .playerHandle(player.getObjectId())
                .title(player.getTitle())
                .playOk1(playOk1)
                .clanId(player.getClanId())
                .female(player.getAppearance().isFemale())
                .race(player.getRace())
                .classId(player.getPlayerClass().getId())
                .x(player.getX())
                .y(player.getY())
                .z(player.getZ())
                .currentHp(player.getCurrentHp())
                .currentMp(player.getCurrentMp())
                .sp(player.getSp())
                .exp(player.getExp())
                .level(player.getLevel())
                .karma(player.getKarma())
                .pkKills(player.getPkKills())
                .intelligence(player.getINT())
                .strength(player.getSTR())
                .constitution(player.getCON())
                .mentality(player.getMEN())
                .dexterity(player.getDEX())
                .wit(player.getWIT())
                .gameMinute(gameMinute)
                .build();
    }
}
