/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.network.codec.PacketWriter;

public class CharSelected extends ServerPacket {
    private final Player player;
    private final int sessionId;

    public CharSelected(Player player, int sessionId) {
        this.player = player;
        this.sessionId = sessionId;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.CHARACTER_SELECTED.debug(this);
        app.l2nx.protocol.game.p267.serverpackets.CharSelected.builder()
                .name(player.getName())
                .objectId(player.getObjectId())
                .title(player.getTitle())
                .playOk1(sessionId)
                .clanId(player.getClanId())
                .female(player.getAppearance().isFemale())
                .race(player.getRace().ordinal())
                .classId(player.getPlayerClass().getId())
                .x(player.getX())
                .y(player.getY())
                .z(player.getZ())
                .currentHp(player.getCurrentHp())
                .currentMp(player.getCurrentMp())
                .sp((int) player.getSp())
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
                .gameMinute(GameTimeTaskManager.getInstance().getGameTime() % (24 * 60)) // "reset" on 24th hour
                .build()
                .write(buffer.buffer());
    }
}
