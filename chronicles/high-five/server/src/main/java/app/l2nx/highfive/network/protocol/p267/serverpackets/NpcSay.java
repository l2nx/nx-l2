/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.NpcStringId.NSLocalisation;
import app.l2nx.highfive.network.protocol.p267.P267ChatTypes;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Kerberos
 */
public class NpcSay extends ServerPacket {
    private final int objectId;
    private final ChatType textType;
    private final int npcId;
    private String text;
    private final int npcString;
    private List<String> parameters;

    /**
     * @param objectId
     * @param messageType
     * @param npcId
     * @param text
     */
    public NpcSay(int objectId, ChatType messageType, int npcId, String text) {
        this.objectId = objectId;
        textType = messageType;
        this.npcId = 1000000 + npcId;
        npcString = -1;
        this.text = text;
    }

    public NpcSay(Npc npc, ChatType messageType, String text) {
        objectId = npc.getObjectId();
        textType = messageType;
        npcId = 1000000 + npc.getTemplate().getDisplayId();
        npcString = -1;
        this.text = text;
    }

    public NpcSay(int objectId, ChatType messageType, int npcId, NpcStringId npcString) {
        this.objectId = objectId;
        textType = messageType;
        this.npcId = 1000000 + npcId;
        this.npcString = npcString.getId();
    }

    public NpcSay(Npc npc, ChatType messageType, NpcStringId npcString) {
        objectId = npc.getObjectId();
        textType = messageType;
        npcId = 1000000 + npc.getTemplate().getDisplayId();
        this.npcString = npcString.getId();
    }

    /**
     * @param text the text to add as a parameter for this packet's message (replaces S1, S2 etc.)
     * @return this NpcSay packet object
     */
    public NpcSay addStringParameter(String text) {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }

        parameters.add(text);
        return this;
    }

    /**
     * @param params a list of strings to add as parameters for this packet's message (replaces S1, S2 etc.)
     * @return this NpcSay packet object
     */
    public NpcSay addStringParameters(String... params) {
        if ((params != null) && (params.length > 0)) {
            if (parameters == null) {
                parameters = new ArrayList<>();
            }

            for (String item : params) {
                if ((item != null) && !item.isEmpty()) {
                    parameters.add(item);
                }
            }
        }

        return this;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.NPC_SAY.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(P267ChatTypes.channel(textType).code());
        buffer.writeInt(npcId);

        // Localisation related.
        if (MultilingualSupportConfig.MULTILANG_ENABLE) {
            final Player player = client.getPlayer();
            if (player != null) {
                final String lang = player.getLang();
                if ((lang != null) && !lang.equals("en")) {
                    final NpcStringId ns = NpcStringId.getNpcStringId(npcString);
                    if (ns != null) {
                        final NSLocalisation nsl = ns.getLocalisation(lang);
                        if (nsl != null) {
                            buffer.writeInt(-1);
                            buffer.writeString(
                                    nsl.getLocalisation(parameters != null ? parameters : Collections.emptyList()));
                            return;
                        }
                    }
                }
            }
        }

        buffer.writeInt(npcString);
        if (npcString == -1) {
            buffer.writeString(text);
        } else if (parameters != null) {
            for (String s : parameters) {
                buffer.writeString(s);
            }
        }
    }
}
