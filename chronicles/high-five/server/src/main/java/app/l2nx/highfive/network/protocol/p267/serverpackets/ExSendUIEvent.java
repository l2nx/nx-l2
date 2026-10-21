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
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.network.codec.PacketWriter;
import java.util.Arrays;
import java.util.List;

public class ExSendUIEvent extends ServerPacket {
    private final int objectId;
    private final boolean type;
    private final boolean countUp;
    private final int startTime;
    private final int endTime;
    private final int npcstringId;
    private List<String> params = null;

    /**
     * @param player
     * @param hide
     * @param countUp
     * @param startTime
     * @param endTime
     * @param text
     */
    public ExSendUIEvent(Player player, boolean hide, boolean countUp, int startTime, int endTime, String text) {
        this(player, hide, countUp, startTime, endTime, -1, text);
    }

    /**
     * @param player
     * @param hide
     * @param countUp
     * @param startTime
     * @param endTime
     * @param npcString
     * @param params
     */
    public ExSendUIEvent(
            Player player,
            boolean hide,
            boolean countUp,
            int startTime,
            int endTime,
            NpcStringId npcString,
            String... params) {
        this(player, hide, countUp, startTime, endTime, npcString.getId(), params);
    }

    /**
     * @param player
     * @param hide
     * @param countUp
     * @param startTime
     * @param endTime
     * @param npcstringId
     * @param params
     */
    public ExSendUIEvent(
            Player player,
            boolean hide,
            boolean countUp,
            int startTime,
            int endTime,
            int npcstringId,
            String... params) {
        objectId = player.getObjectId();
        type = hide;
        this.countUp = countUp;
        this.startTime = startTime;
        this.endTime = endTime;
        this.npcstringId = npcstringId;
        this.params = Arrays.asList(params);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SEND_UI_EVENT.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(
                type); // 0 = show, 1 = hide (there is 2 = pause and 3 = resume also but they don't work well you can
        // only pause count down and you cannot resume it because resume hides the counter).
        buffer.writeInt(0); // unknown
        buffer.writeInt(0); // unknown
        buffer.writeString(countUp ? "1" : "0"); // 0 = count down, 1 = count up
        // timer always disappears 10 seconds before end
        buffer.writeString(String.valueOf(startTime / 60));
        buffer.writeString(String.valueOf(startTime % 60));
        buffer.writeString(String.valueOf(endTime / 60));
        buffer.writeString(String.valueOf(endTime % 60));
        buffer.writeInt(npcstringId);
        if (params != null) {
            for (String param : params) {
                buffer.writeString(param);
            }
        }
    }
}
