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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.network.buffer.WriteBuffer;

public class CharSelected extends ServerPacket {
    private final Player player;
    private final int sessionId;

    public CharSelected(Player player, int sessionId) {
        this.player = player;
        this.sessionId = sessionId;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.CHARACTER_SELECTED.writeId(this, buffer);
        buffer.writeString(player.getName());
        buffer.writeInt(player.getObjectId());
        buffer.writeString(player.getTitle());
        buffer.writeInt(sessionId);
        buffer.writeInt(player.getClanId());
        buffer.writeInt(0); // ??
        buffer.writeInt(player.getAppearance().isFemale());
        buffer.writeInt(player.getRace().ordinal());
        buffer.writeInt(player.getPlayerClass().getId());
        buffer.writeInt(1); // active ??
        buffer.writeInt(player.getX());
        buffer.writeInt(player.getY());
        buffer.writeInt(player.getZ());
        buffer.writeDouble(player.getCurrentHp());
        buffer.writeDouble(player.getCurrentMp());
        buffer.writeInt((int) player.getSp());
        buffer.writeLong(player.getExp());
        buffer.writeInt(player.getLevel());
        buffer.writeInt(player.getKarma()); // thx evill33t
        buffer.writeInt(player.getPkKills());
        buffer.writeInt(player.getINT());
        buffer.writeInt(player.getSTR());
        buffer.writeInt(player.getCON());
        buffer.writeInt(player.getMEN());
        buffer.writeInt(player.getDEX());
        buffer.writeInt(player.getWIT());
        buffer.writeInt(GameTimeTaskManager.getInstance().getGameTime() % (24 * 60)); // "reset" on 24th hour
        buffer.writeInt(0);
        buffer.writeInt(player.getPlayerClass().getId());
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeBytes(new byte[64]);
        buffer.writeInt(0);
    }
}
