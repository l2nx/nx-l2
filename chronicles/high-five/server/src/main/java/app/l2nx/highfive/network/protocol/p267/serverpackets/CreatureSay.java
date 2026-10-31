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

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.P267ChatTypes;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class CreatureSay extends ServerPacket {
    private final Creature sender;
    private final ChatType chatType;
    private String senderName = null;
    private String text = null;
    private int charId = 0;
    private int messageId = -1;
    private List<String> parameters;

    public CreatureSay(Creature sender, ChatType chatType, String senderName, String text) {
        this.sender = sender;
        this.chatType = chatType;
        this.senderName = senderName;
        this.text = text;
    }

    public CreatureSay(Creature sender, ChatType chatType, NpcStringId npcStringId) {
        this.sender = sender;
        this.chatType = chatType;
        messageId = npcStringId.getId();
        if (sender != null) {
            senderName = sender.getName();
        }
    }

    public CreatureSay(ChatType chatType, int charId, SystemMessageId systemMessageId) {
        sender = null;
        this.chatType = chatType;
        this.charId = charId;
        messageId = systemMessageId.getId();
    }

    /**
     * String parameter for argument S1,S2,.. in npcstring-e.dat
     * @param text
     */
    public void addStringParameter(String text) {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }

        parameters.add(text);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SAY2.debug(this);
        app.l2nx.protocol.game.p267.serverpackets.CreatureSay.builder()
                .senderId(sender == null ? 0 : sender.getObjectId())
                .channel(P267ChatTypes.channel(chatType))
                .senderName(senderName)
                .senderNameId(charId)
                .messageId(messageId)
                .text(text)
                .parameters(parameters)
                .build()
                .write(buffer.buffer());
    }

    @Override
    public void runImpl(Player player) {
        if (player != null) {
            player.broadcastSnoop(chatType, senderName, text, this);
        }
    }
}
